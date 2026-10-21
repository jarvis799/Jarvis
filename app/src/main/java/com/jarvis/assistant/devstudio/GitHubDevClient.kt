package com.jarvis.assistant.devstudio

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.Base64
import java.util.concurrent.TimeUnit

/**
 * GitHub operations for the Developer Studio: verify the connection, create a repository
 * and push validated project files through the Git Data API.
 */
class GitHubDevClient(private val token: String) {

    companion object {
        private const val API = "https://api.github.com"
    }

    data class PublishResult(val repoUrl: String, val commitSha: String, val commitUrl: String, val fileCount: Int, val skipped: List<String>)

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    private val json = "application/json".toMediaType()

    private fun rawCall(method: String, path: String, body: JSONObject? = null): String {
        check(token.isNotBlank()) { "Connect GitHub first." }
        val builder = Request.Builder().url(if (path.startsWith("http")) path else API + path)
            .header("Accept", "application/vnd.github+json")
            .header("Authorization", "Bearer " + token)
            .header("User-Agent", "Jarvis-DevStudio")
        when (method) {
            "GET" -> builder.get()
            "POST" -> builder.post((body ?: JSONObject()).toString().toRequestBody(json))
            "PATCH" -> builder.patch((body ?: JSONObject()).toString().toRequestBody(json))
            else -> builder.get()
        }
        client.newCall(builder.build()).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val reason = runCatching { JSONObject(text).optString("message") }.getOrDefault("")
                val friendly = when (response.code) {
                    401 -> "GitHub rejected the token. Create a new token with repo scope."
                    403 -> "GitHub refused the request (rate limit or missing permission)."
                    404 -> "GitHub could not find that repository or branch."
                    422 -> "GitHub rejected the request: " + reason
                    else -> "GitHub returned HTTP " + response.code + (if (reason.isNotBlank()) " - " + reason else "")
                }
                throw IllegalStateException(friendly)
            }
            return text
        }
    }

    private fun call(method: String, path: String, body: JSONObject? = null): JSONObject {
        val text = rawCall(method, path, body)
        return if (text.isBlank()) JSONObject() else JSONObject(text)
    }

    private fun encode(content: String): String =
        Base64.getEncoder().encodeToString(content.toByteArray(Charsets.UTF_8))

    /** Returns the authenticated login, or throws with an actionable message. */
    fun whoami(): String {
        val user = call("GET", "/user")
        return user.optString("login").ifBlank { throw IllegalStateException("GitHub did not return an account name.") }
    }

    fun listRepos(): List<String> {
        val text = rawCall("GET", "/user/repos?per_page=100&sort=updated&affiliation=owner")
        if (text.isBlank()) return emptyList()
        val arr = JSONArray(text)
        val found = mutableListOf<String>()
        for (i in 0 until arr.length()) {
            val full = arr.getJSONObject(i).optString("full_name")
            if (full.isNotBlank()) found.add(full)
        }
        return found
    }

    fun createRepo(name: String, privateRepo: Boolean): String {
        val body = JSONObject().put("name", name).put("private", privateRepo).put("auto_init", false)
        val created = call("POST", "/user/repos", body)
        return created.optString("full_name").ifBlank { throw IllegalStateException("GitHub did not return the new repository.") }
    }

    fun repoUrl(repoFullName: String): String = call("GET", "/repos/" + repoFullName).optString("html_url")

    fun defaultBranch(repoFullName: String): String =
        call("GET", "/repos/" + repoFullName).optString("default_branch").ifBlank { "main" }

    private fun refSha(repoFullName: String, branch: String): JSONObject? = runCatching {
        call("GET", "/repos/" + repoFullName + "/git/ref/heads/" + branch)
    }.getOrNull()

    /** Creates blobs, a tree, a commit and updates the branch ref. */
    fun publish(
        repoFullName: String,
        branch: String,
        files: List<ProjectFile>,
        commitMessage: String,
        includeBuildOutput: Boolean = false
    ): PublishResult {
        val publishable = files.filter { !DevStudioRules.isExcludedPath(it.path, includeBuildOutput) }
        val skipped = files.filter { DevStudioRules.isExcludedPath(it.path, includeBuildOutput) }.map { it.path }
        require(publishable.isNotEmpty()) { "There is nothing to publish yet." }

        val existing = refSha(repoFullName, branch)
        val parentSha = existing?.optJSONObject("object")?.optString("sha")

        val treeEntries = JSONArray()
        publishable.forEach { file ->
            val blob = call("POST", "/repos/" + repoFullName + "/git/blobs", JSONObject()
                .put("content", encode(file.content)).put("encoding", "base64"))
            treeEntries.put(JSONObject()
                .put("path", file.path)
                .put("mode", "100644")
                .put("type", "blob")
                .put("sha", blob.optString("sha")))
            Thread.sleep(150)
        }

        val treeBody = JSONObject().put("tree", treeEntries)
        if (parentSha != null) treeBody.put("base_tree", parentSha)
        val tree = call("POST", "/repos/" + repoFullName + "/git/trees", treeBody)

        val commitBody = JSONObject().put("message", commitMessage).put("tree", tree.optString("sha"))
        if (parentSha != null) commitBody.put("parents", JSONArray().put(parentSha))
        val commit = call("POST", "/repos/" + repoFullName + "/git/commits", commitBody)
        val commitSha = commit.optString("sha")

        if (parentSha != null) {
            call("PATCH", "/repos/" + repoFullName + "/git/refs/heads/" + branch, JSONObject().put("sha", commitSha).put("force", false))
        } else {
            call("POST", "/repos/" + repoFullName + "/git/refs", JSONObject().put("ref", "refs/heads/" + branch).put("sha", commitSha))
        }

        val verified = call("GET", "/repos/" + repoFullName + "/commits/" + commitSha)
        val verifiedSha = verified.optString("sha")
        check(verifiedSha == commitSha) { "GitHub did not confirm the pushed commit." }
        val url = call("GET", "/repos/" + repoFullName).optString("html_url")
        return PublishResult(url, commitSha, verified.optString("html_url"), publishable.size, skipped)
    }
}
