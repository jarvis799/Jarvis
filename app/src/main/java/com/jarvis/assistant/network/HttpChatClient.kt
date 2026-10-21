package com.jarvis.assistant.network

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * DEV STYLE: any HTTPS chat API works here - prompt-URL GET wrappers
 * (r-gengpt style), OpenAI-compatible POST bodies and plain prompt POSTs,
 * with graceful fallbacks between formats. Only the user's own optional key
 * is ever sent.
 */
class HttpChatClient {
    companion object {
        private const val MAX_REPLY_BYTES = 65536L
        private val client = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS).callTimeout(40, TimeUnit.SECONDS)
            .followRedirects(false).followSslRedirects(false).build()
    }

    suspend fun ask(endpoint: String, prompt: String, apiKey: String = "", model: String = ""): String {
        var lastError: Exception? = null
        for (variant in requestVariants(endpoint, prompt, model)) {
            try {
                return requestOnce(variant.first, variant.second, apiKey)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError ?: IOException("Endpoint request failed.")
    }

    /**
     * Prompt-URL GET first (the format these wrappers advertise), then
     * OpenAI messages POST, then prompt/input JSON POSTs as fallbacks.
     */
    private fun requestVariants(endpoint: String, prompt: String, model: String): List<Pair<String, RequestBody?>> {
        require(endpoint.isNotBlank() && EndpointConfig.validationError(endpoint) == null && endpoint.startsWith("https://", true)) { "Configure your own HTTPS endpoint in Settings." }
        require(prompt.isNotBlank() && prompt.length <= 8000) { "Please use 1 to 8000 characters." }
        return if (EndpointConfig.usesGet(endpoint)) {
            listOf(
                EndpointConfig.promptUrl(endpoint, prompt) to null,
                endpoint to jsonBody(prompt, model, "prompt"),
                endpoint to jsonBody(prompt, model, "input")
            )
        } else {
            listOf(
                endpoint to openAiBody(prompt, model),
                endpoint to jsonBody(prompt, model, "prompt"),
                endpoint to jsonBody(prompt, model, "input")
            )
        }
    }

    private fun openAiBody(prompt: String, model: String): RequestBody {
        val messages = JSONArray().put(JSONObject().put("role", "user").put("content", prompt))
        val payload = JSONObject().put("messages", messages).put("stream", false)
        if (model.isNotBlank()) payload.put("model", model)
        return payload.toString().toRequestBody("application/json".toMediaType())
    }

    private fun jsonBody(prompt: String, model: String, key: String): RequestBody {
        val payload = JSONObject().put(key, prompt)
        if (model.isNotBlank()) payload.put("model", model)
        return payload.toString().toRequestBody("application/json".toMediaType())
    }

    private suspend fun requestOnce(url: String, body: RequestBody?, apiKey: String): String = suspendCancellableCoroutine { continuation ->
        val request = runCatching {
            val builder = Request.Builder().header("Accept", "text/plain, application/json")
            if (apiKey.isNotBlank()) builder.header("Authorization", "Bearer " + apiKey)
            if (body == null) builder.url(url).get() else builder.url(url).post(body)
            builder.build()
        }.getOrElse { continuation.resumeWithException(it); return@suspendCancellableCoroutine }
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { if (continuation.isActive) continuation.resumeWithException(e) }
            override fun onResponse(call: Call, response: Response) {
                val result = runCatching { response.use {
                    check(it.isSuccessful) { "Custom endpoint returned HTTP " + it.code }
                    val respBody = checkNotNull(it.body) { "Empty endpoint response." }
                    check(respBody.contentLength() <= MAX_REPLY_BYTES) { "Response too large." }
                    val source = respBody.source()
                    source.request(MAX_REPLY_BYTES + 1)
                    check(source.buffer.size <= MAX_REPLY_BYTES) { "Response too large." }
                    decodeReply(source.readUtf8())
                } }
                if (continuation.isActive) result.fold({ continuation.resume(it) }, { continuation.resumeWithException(it) })
            }
        })
    }

    /** DEV STYLE: tolerant reply decoder - every common AI API response shape is understood. */
    private fun decodeReply(raw: String): String {
        var text = raw.trim()
        check(text.isNotEmpty() && !text.startsWith("<")) { "Endpoint did not return a chat response." }
        // Some wrappers wrap the JSON in a markdown code fence.
        if (text.startsWith("```")) {
            val nl = text.indexOf('\n')
            val endFence = text.lastIndexOf("```")
            if (nl > 0 && endFence > nl) text = text.substring(nl + 1, endFence).trim()
        }
        if (!text.startsWith("{") && !text.startsWith("[") && !text.startsWith("\"")) return text
        val value = JSONTokener(text).nextValue()
        if (value is String) return value.trim().also { check(it.isNotEmpty()) }
        val json = (value as? JSONArray)?.optJSONObject(0) ?: value as? JSONObject ?: error("Unsupported response format.")
        check(!json.has("error") || json.isNull("error") || json.optString("error").isBlank()) { "Endpoint rejected this request. Check your settings." }
        val choice = json.optJSONArray("choices")?.optJSONObject(0)
        val compatible = choice?.optJSONObject("message")?.optString("content").orEmpty().ifBlank { choice?.optString("text").orEmpty() }
        if (compatible.isNotBlank()) return compatible.trim()
        val gemini = json.optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")
            ?.optJSONArray("parts")?.optJSONObject(0)?.optString("text").orEmpty()
        if (gemini.isNotBlank()) return gemini.trim()
        val ollama = json.optJSONObject("message")?.optString("content").orEmpty()
        if (ollama.isNotBlank()) return ollama.trim()
        for (obj in listOfNotNull(json, json.optJSONObject("data"))) {
            for (key in listOf("reply", "response", "answer", "output_text", "output", "text", "message", "content", "result", "generated_text", "data")) {
                val reply = obj.opt(key)
                if (reply is String && reply.isNotBlank()) return reply.trim()
            }
        }
        error("Endpoint returned JSON without a text reply.")
    }
}
