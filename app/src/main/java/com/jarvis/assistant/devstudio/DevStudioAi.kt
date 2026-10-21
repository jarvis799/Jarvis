package com.jarvis.assistant.devstudio

import com.jarvis.assistant.JarvisApp
import com.jarvis.assistant.network.EndpointConfig
import com.jarvis.assistant.network.HttpChatClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Uses the AI provider already configured in Settings (custom HTTPS endpoint or the
 * personal Gemini key). No new backend and no credentials leave the device beyond the
 * provider the user already configured.
 */
object DevStudioAi {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val json = "application/json; charset=utf-8".toMediaType()

    suspend fun ask(prompt: String): String = withContext(Dispatchers.IO) {
        val prefs = JarvisApp.instance.preferences
        val apiKey = prefs.apiKey.trim()
        val endpoint = EndpointConfig.httpEndpoint(apiKey, prefs.customEndpoint)
        if (endpoint != null) {
            return@withContext HttpChatClient().ask(endpoint, prompt, apiKey, prefs.customChatModel)
        }
        if (apiKey.isNotBlank()) return@withContext gemini(apiKey, prefs.customChatModel, prompt)
        throw IllegalStateException("Connect your AI provider first in Settings - Gemini Live API & Model.")
    }

    /** One bounded retry so a transient provider error does not kill a project run. */
    suspend fun askWithRetry(prompt: String, attempts: Int = 2): String {
        var last: Exception? = null
        repeat(attempts) { index ->
            try {
                return ask(prompt)
            } catch (e: Exception) {
                last = e
                if (index < attempts - 1) delay(1500L * (index + 1))
            }
        }
        throw IllegalStateException(last?.message ?: "The AI provider did not answer.", last)
    }

    private fun gemini(apiKey: String, model: String, prompt: String): String {
        val chosen = model.trim().ifBlank { com.jarvis.assistant.data.model.GeminiConstants.DEFAULT_MODEL }
        val payload = JSONObject()
            .put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt)))))
        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/" + chosen + ":generateContent")
            .header("x-goog-api-key", apiKey)
            .post(payload.toString().toRequestBody(json))
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IllegalStateException("AI provider returned HTTP " + response.code + ".")
            return geminiText(body)
        }
    }

    private fun geminiText(body: String): String {
        val candidates = JSONObject(body).optJSONArray("candidates") ?: return ""
        val parts = candidates.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts") ?: return ""
        val sb = StringBuilder()
        for (i in 0 until parts.length()) sb.append(parts.optJSONObject(i)?.optString("text").orEmpty())
        return sb.toString()
    }
}
