package com.jarvis.assistant.devstudio

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.Base64
import java.util.concurrent.TimeUnit

/** Vercel connection test and deployment of a generated web project. Never deploys without approval. */
class VercelClient(private val token: String) {

    companion object {
        private const val API = "https://api.vercel.com"
        private const val MAX_FILES = 200
    }

    data class DeployResult(val id: String, val url: String, val state: String, val fileCount: Int)

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(180, TimeUnit.SECONDS)
        .build()

    private val json = "application/json".toMediaType()

    private fun call(method: String, path: String, body: JSONObject? = null): JSONObject {
        check(token.isNotBlank()) { "Connect Vercel first." }
        val builder = Request.Builder().url(API + path)
            .header("Accept", "application/json")
            .header("Authorization", "Bearer " + token)
            .header("User-Agent", "Jarvis-DevStudio")
        when (method) {
            "GET" -> builder.get()
            "POST" -> builder.post((body ?: JSONObject()).toString().toRequestBody(json))
            "PATCH" -> builder.patch((body ?: JSONObject()).toString().toRequestBody(json))
            "DELETE" -> builder.delete()
            else -> builder.get()
        }
        client.newCall(builder.build()).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val reason = runCatching { JSONObject(text).optJSONObject("error")?.optString("message") ?: JSONObject(text).optString("message") }.getOrDefault("")
                val friendly = when (response.code) {
                    401, 403 -> "Vercel rejected the token. Create a new token with full account access."
                    404 -> "Vercel could not find that project."
                    409 -> "Vercel reported a name conflict."
                    else -> "Vercel returned HTTP " + response.code + (if (reason.isNotBlank()) " - " + reason else "")
                }
                throw IllegalStateException(friendly)
            }
            return if (text.isBlank()) JSONObject() else JSONObject(text)
        }
    }

    fun whoami(): String {
        val user = call("GET", "/v2/user").optJSONObject("user")
        return user?.optString("username")?.ifBlank { user.optString("email") }
            ?: throw IllegalStateException("Vercel did not return the authenticated account.")
    }

    fun deploy(projectName: String, files: List<ProjectFile>, target: String = "production"): DeployResult {
        val publishable = files.filter { !DevStudioRules.isExcludedPath(it.path) }.take(MAX_FILES)
        require(publishable.isNotEmpty()) { "There is nothing to deploy yet." }
        val arr = JSONArray()
        publishable.forEach { file ->
            arr.put(JSONObject()
                .put("file", file.path)
                .put("data", Base64.getEncoder().encodeToString(file.content.toByteArray(Charsets.UTF_8)))
                .put("encoding", "base64"))
        }
        val body = JSONObject()
            .put("name", DevStudioRules.sanitizeProjectName(projectName))
            .put("files", arr)
            .put("target", target)
            .put("projectSettings", JSONObject().put("framework", JSONObject.NULL))
        val created = call("POST", "/v13/deployments", body)
        val id = created.optString("id").ifBlank { throw IllegalStateException("Vercel did not return a deployment id.") }

        var state = created.optString("readyState", "QUEUED")
        var url = created.optString("url")
        var attempt = 0
        while (attempt < 40 && state != "READY" && state != "ERROR" && state != "CANCELED") {
            Thread.sleep(3000)
            val polled = call("GET", "/v13/deployments/" + id)
            state = polled.optString("readyState", state)
            url = polled.optString("url", url)
            attempt++
        }
        if (state == "ERROR" || state == "CANCELED") {
            throw IllegalStateException("Vercel deployment " + state.lowercase() + ". Check the build log in Vercel.")
        }
        check(state == "READY") { "Vercel deployment is still building (" + state.lowercase() + "). Check it in the Vercel dashboard." }
        return DeployResult(id, if (url.startsWith("http")) url else "https://" + url, state, publishable.size)
    }
}
