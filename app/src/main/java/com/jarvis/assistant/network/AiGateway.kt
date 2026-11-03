package com.jarvis.assistant.network

import android.content.Context
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
        val fresh = fresh(context)
        // Everything cooling down? Give them one more honest try instead of staying mute.
        return attemptFirst(if (fresh.isEmpty()) all else fresh, prompt, model, onDelta)
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
}
