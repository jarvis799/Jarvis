package com.jarvis.assistant.agent.crew

import android.content.Context
import com.jarvis.assistant.network.AiGateway
import com.jarvis.assistant.network.AiProvider
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

/**
 * One place every agent call goes through, so timeout, retry and fallback behave the same for all
 * three roles. The first attempt targets the role's own provider; if that fails or times out, the
 * normal ordered provider chain answers. Nothing here is allowed to hang a turn: every call has a
 * deadline.
 */
object CrewCall {

    private const val CALL_TIMEOUT_MS = 25_000L

    suspend fun ask(context: Context, provider: AiProvider?, prompt: String): String? {
        if (provider != null) {
            val direct = runCatching {
                withTimeout(CALL_TIMEOUT_MS) { AiGateway.askWith(context, provider.id, prompt) }
            }.getOrNull()
            if (!direct.isNullOrBlank()) return direct
        }
        return runCatching {
            withTimeout(CALL_TIMEOUT_MS) { AiGateway.ask(context, prompt) }
        }.getOrNull()
    }

    /** Pulls the first JSON object out of a reply that may carry prose around it. */
    fun json(raw: String): JSONObject? {
        val text = raw.trim()
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return runCatching { JSONObject(text.substring(start, end + 1)) }.getOrNull()
    }

    fun strings(array: org.json.JSONArray?): List<String> {
        if (array == null) return emptyList()
        val out = ArrayList<String>()
        for (index in 0 until array.length()) {
            val value = array.optString(index, "").trim()
            if (value.isNotEmpty()) out.add(value)
        }
        return out
    }
}
