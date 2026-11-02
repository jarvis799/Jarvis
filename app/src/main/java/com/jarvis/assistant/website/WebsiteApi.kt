package com.jarvis.assistant.website

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * DEV STYLE: live client for Jarvis's own website API.
 *
 * Public actions work for anyone. Admin actions carry the agent key in the
 * x-admin-token header - only a verified creator can save that key, and the
 * website itself is the one that decides whether it is valid.
 */
object WebsiteApi {

    const val BASE = "https://jarvis-asistantt.base44.app"
    private const val PUBLIC_URL = "$BASE/functions/publicApi"
    private const val ADMIN_URL = "$BASE/functions/adminApi"

    data class Reply(val ok: Boolean, val code: Int, val body: String, val error: String?)

    suspend fun publicCall(action: String, payload: JSONObject = JSONObject()): Reply =
        post(PUBLIC_URL, payload.put("action", action), null)

    suspend fun adminCall(action: String, payload: JSONObject, agentKey: String): Reply =
        post(ADMIN_URL, payload.put("action", action), agentKey)

    suspend fun creatorVerify(pass: String): Reply =
        publicCall("creatorVerify", JSONObject().put("pass", pass))

    private suspend fun post(url: String, payload: JSONObject, adminToken: String?): Reply =
        withContext(Dispatchers.IO) {
            var connection: HttpURLConnection? = null
            try {
                connection = (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 12000
                    readTimeout = 20000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    setRequestProperty("Accept", "application/json")
                    if (!adminToken.isNullOrBlank()) setRequestProperty("x-admin-token", adminToken)
                }
                connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                Reply(
                    ok = code in 200..299,
                    code = code,
                    body = body,
                    error = if (code in 200..299) null else "HTTP $code" + explain(code)
                )
            } catch (error: Exception) {
                Reply(false, 0, "", error.message ?: "Network error")
            } finally {
                connection?.disconnect()
            }
        }

    private fun explain(code: Int): String = when (code) {
        400 -> " - the request body was rejected"
        401 -> " - admin token rejected"
        403 -> " - not allowed"
        404 -> " - not found"
        409 -> " - conflict"
        423 -> " - locked"
        429 -> " - rate limited or blocked"
        else -> ""
    }

    /** Reads one top-level field of a JSON reply without throwing on HTML or empty bodies. */
    fun field(reply: Reply, key: String): String =
        runCatching { JSONObject(reply.body).optString(key) }.getOrDefault("")

    fun flag(reply: Reply, key: String): Boolean =
        runCatching { JSONObject(reply.body).optBoolean(key) }.getOrDefault(false)
}
