package com.jarvis.assistant.network

import android.content.Context
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * One answer, many brains. Providers are tried in the order the owner saved them: a quota
 * wall (429), a dead key (401/403) or a timeout pushes the turn to the next entry, so the
 * assistant never goes silent because one endpoint ran out of credit.
 */
object AiGateway {

    private const val TAG = "AiGateway"
    private const val GEMINI_BASE = "https://generativelanguage.googleapis.com/v1beta/models/"
    private const val FALLBACK_MODEL = "gemini-2.5-flash"
    private const val COOLDOWN_MS = 10 * 60 * 1000L

    private val cooling = ConcurrentHashMap<String, Long>()
    private val chats = HttpChatClient()
    private val http = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(18, TimeUnit.SECONDS)
        .build()

    fun hasProviders(context: Context): Boolean = AiProviderStore.load(context).isNotEmpty()

    private fun fresh(context: Context): List<AiProvider> {
        val now = System.currentTimeMillis()
        return AiProviderStore.load(context).filter { (cooling[it.id] ?: 0L) < now }
    }

    suspend fun ask(context: Context, prompt: String, model: String = ""): String =
        run(context, prompt, model, null)

    suspend fun stream(context: Context, prompt: String, model: String = "", onDelta: (String) -> Unit): String =
        run(context, prompt, model, onDelta)

    private suspend fun run(
        context: Context,
        prompt: String,
        model: String,
        onDelta: ((String) -> Unit)?
    ): String {
        val all = AiProviderStore.load(context)
        if (all.isEmpty()) throw IllegalStateException(
            "No AI brain is set up. Settings > API Connection > add a Gemini key or your own endpoint."
        )
        val available = fresh(context)
        // Everything cooling down? Give them one more honest try instead of staying mute.
        return attemptFirst(if (available.isEmpty()) all else available, prompt, model, onDelta)
    }

    private suspend fun attemptFirst(
        providers: List<AiProvider>,
        prompt: String,
        model: String,
        onDelta: ((String) -> Unit)?
    ): String {
        var last: Exception? = null
        for (provider in providers) {
            try {
                return attempt(provider, prompt, model, onDelta)
            } catch (cancel: kotlinx.coroutines.CancellationException) {
                throw cancel
            } catch (error: Exception) {
                last = error
                cool(provider, error)
                Log.w(TAG, "provider " + provider.label + " failed: " + error.message)
            }
        }
        throw last ?: IllegalStateException("Every AI brain failed.")
    }

    private fun cool(provider: AiProvider, error: Exception) {
        val text = (error.message ?: "").lowercase()
        val switch = text.contains("429") || text.contains("quota") || text.contains("limit") ||
            text.contains("401") || text.contains("403") || text.contains("api key") ||
            text.contains("invalid") || text.contains("credits")
        if (switch) cooling[provider.id] = System.currentTimeMillis() + COOLDOWN_MS
    }

    private suspend fun attempt(
        provider: AiProvider,
        prompt: String,
        model: String,
        onDelta: ((String) -> Unit)?
    ): String =
        if (provider.kind == AiProvider.KIND_GEMINI) gemini(provider, prompt, model, onDelta)
        else custom(provider, prompt, model, onDelta)

    private suspend fun custom(
        provider: AiProvider,
        prompt: String,
        model: String,
        onDelta: ((String) -> Unit)?
    ): String {
        val chosen = model.ifBlank { provider.model }
        if (onDelta != null && !EndpointConfig.usesGet(provider.endpoint)) {
            val streamed = chats.stream(provider.endpoint, prompt, provider.key, chosen) { delta -> onDelta(delta) }
            if (streamed.isNotBlank()) return streamed
        }
        return chats.ask(provider.endpoint, prompt, provider.key, chosen)
    }

    private suspend fun gemini(
        provider: AiProvider,
        prompt: String,
        model: String,
        onDelta: ((String) -> Unit)?
    ): String {
        val key = provider.key.trim()
        if (key.isBlank()) throw IOException("Gemini key is missing.")
        val answer = geminiCall(key, geminiModel(model.ifBlank { provider.model }), prompt)
        if (onDelta != null && answer.isNotBlank()) onDelta(answer)
        return answer
    }

    private fun geminiModel(candidate: String): String {
        val cleaned = candidate.substringAfter("models/").trim()
        return if (cleaned.startsWith("gemini-") && cleaned.length > 8) cleaned else FALLBACK_MODEL
    }

    private suspend fun geminiCall(key: String, model: String, prompt: String): String {
        val payload = JSONObject()
            .put(
                "contents",
                JSONArray().put(
                    JSONObject().put("role", "user")
                        .put("parts", JSONArray().put(JSONObject().put("text", prompt)))
                )
            )
            .put("generationConfig", JSONObject().put("maxOutputTokens", 512).put("temperature", 0.6))
            .toString()
            .toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(GEMINI_BASE + model + ":generateContent?key=" + key)
            .header("Content-Type", "application/json")
            .post(payload)
            .build()
        val result = withContext(Dispatchers.IO) {
            http.newCall(request).execute().use { response ->
                Pair(response.code, response.body?.string().orEmpty())
            }
        }
        if (result.first !in 200..299) throw IOException("Gemini HTTP " + result.first + " " + result.second.take(160))
        val parts = JSONObject(result.second).optJSONArray("candidates")
            ?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
            ?: throw IOException("Gemini returned no answer.")
        val text = StringBuilder()
        for (index in 0 until parts.length()) {
            text.append(parts.optJSONObject(index)?.optString("text").orEmpty())
        }
        val answer = text.toString().trim()
        if (answer.isBlank()) throw IOException("Gemini returned an empty answer.")
        return answer
    }

    /**
     * Turns a recorded utterance into text.
     *
     * The live socket normally returns its own transcript, but when it stays mute the app is
     * left with the microphone recording and no words. This sends that recording to Gemini's
     * audio input, so a spoken turn still becomes a question the brain can answer.
     */
    suspend fun transcribe(context: Context, pcm16: ByteArray, sampleRate: Int = 16000): String {
        if (pcm16.size < sampleRate) return ""
        val geminiOnly = AiProviderStore.load(context).filter { it.kind == AiProvider.KIND_GEMINI && it.key.isNotBlank() }
        val providers = fresh(context).filter { it.kind == AiProvider.KIND_GEMINI && it.key.isNotBlank() }.ifEmpty { geminiOnly }
        if (providers.isEmpty()) return ""
        val audio = wavBase64(pcm16, sampleRate)
        for (provider in providers) {
            try {
                return transcribeWith(provider.key, audio)
            } catch (cancel: kotlinx.coroutines.CancellationException) {
                throw cancel
            } catch (error: Exception) {
                Log.w(TAG, "transcribe failed on " + provider.label + ": " + error.message)
            }
        }
        return ""
    }

    /** 16-bit mono PCM wrapped in a WAV header; the last 15 seconds are enough for one turn. */
    private fun wavBase64(pcm16: ByteArray, sampleRate: Int): String {
        val maxBytes = sampleRate * 2 * 15
        val pcm = if (pcm16.size > maxBytes) pcm16.copyOfRange(pcm16.size - maxBytes, pcm16.size) else pcm16
        val out = java.io.ByteArrayOutputStream(pcm.size + 44)
        fun le(value: Int, bytes: Int) { for (index in 0 until bytes) out.write((value shr (8 * index)) and 0xFF) }
        out.write("RIFF".toByteArray())
        le(36 + pcm.size, 4)
        out.write("WAVE".toByteArray())
        out.write("fmt ".toByteArray())
        le(16, 4)
        le(1, 2)
        le(1, 2)
        le(sampleRate, 4)
        le(sampleRate * 2, 4)
        le(2, 2)
        le(16, 2)
        out.write("data".toByteArray())
        le(pcm.size, 4)
        out.write(pcm)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    private suspend fun transcribeWith(key: String, audioBase64: String): String {
        val parts = JSONArray()
            .put(JSONObject().put("text", "Write down exactly what is said in this recording. Reply with the spoken words only."))
            .put(
                JSONObject().put(
                    "inline_data",
                    JSONObject().put("mime_type", "audio/wav").put("data", audioBase64)
                )
            )
        val payload = JSONObject()
            .put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", parts)))
            .put("generationConfig", JSONObject().put("maxOutputTokens", 256).put("temperature", 0.0))
            .toString()
            .toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(GEMINI_BASE + FALLBACK_MODEL + ":generateContent?key=" + key)
            .header("Content-Type", "application/json")
            .post(payload)
            .build()
        val result = withContext(Dispatchers.IO) {
            http.newCall(request).execute().use { response ->
                Pair(response.code, response.body?.string().orEmpty())
            }
        }
        if (result.first !in 200..299) throw IOException("Gemini HTTP " + result.first + " " + result.second.take(160))
        val answerParts = JSONObject(result.second).optJSONArray("candidates")
            ?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts") ?: return ""
        val text = StringBuilder()
        for (index in 0 until answerParts.length()) {
            text.append(answerParts.optJSONObject(index)?.optString("text").orEmpty())
        }
        return text.toString().trim()
    }
}
