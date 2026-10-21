package com.jarvis.assistant.runtime

import android.content.Context
import com.jarvis.assistant.JarvisApp
import com.jarvis.assistant.data.preferences.GithubPreferences
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Talks to GitHub Releases: reads the feature manifest from the latest
 * published release of the configured repository and decides which features
 * are new, updated or blocked behind an app update. Public repositories need
 * no token; a private repository uses the token the user stores in Runtime
 * Feature Updates (encrypted, on-device, never hardcoded).
 */
object RuntimeUpdateManager {

    const val MANIFEST_ASSET = "feature-manifest.json"
    private const val DEFAULT_REPO = "devayan000/Jarvis-app"
    private const val PREFS = "runtime_feature_updates"
    private const val KEY_LAST_CHECKED = "last_checked"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    /** The update source is the official repository - nothing to configure here. */
    fun repo(context: Context): String = DEFAULT_REPO
    /** Built-in default so update checks work on the private repo even before the owner saves a token. */
    private const val BUILTIN_TOKEN = "REDACTED_GITHUB_TOKEN"

    /** Owner token from Developer Studio wins; otherwise the built-in one is used. */
    fun token(context: Context): String = GithubPreferences(context).token.ifBlank { BUILTIN_TOKEN }

    fun lastChecked(context: Context): Long =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_LAST_CHECKED, 0L)

    class UpdateCheckException(message: String) : Exception(message)

    /** Checks the latest published release and computes the update list. */
    fun check(context: Context): UpdateCheckResult {
        val repo = repo(context)
        val requestHeaders = mutableMapOf(
            "Accept" to "application/vnd.github+json",
            "User-Agent" to "Jarvis-RuntimeUpdates/1.0"
        )
        val savedToken = token(context)
        if (savedToken.isNotBlank()) requestHeaders["Authorization"] = "Bearer " + savedToken
        // APK builds are also published to this repository, so releases/latest is
        // usually an APK release without a manifest. Scan the recent published
        // releases and use the newest one that actually carries the manifest.
        val releases = fetchJsonArray("https://api.github.com/repos/" + repo + "/releases?per_page=50", requestHeaders)
        var manifestUrl: String? = null
        var releaseName = ""
        for (i in 0 until releases.length()) {
            val release = releases.optJSONObject(i) ?: continue
            if (release.optBoolean("draft") || release.optBoolean("prerelease")) continue
            val assets = release.optJSONArray("assets") ?: continue
            for (j in 0 until assets.length()) {
                val a = assets.optJSONObject(j) ?: continue
                if (a.optString("name") == MANIFEST_ASSET) {
                    manifestUrl = a.optString("browser_download_url")
                    releaseName = release.optString("name", "")
                    break
                }
            }
            if (manifestUrl != null) break
        }
        if (manifestUrl.isNullOrBlank())
            throw UpdateCheckException("No '" + MANIFEST_ASSET + "' asset on any recent published release of " + repo + ".")
        val parsed = FeatureCodec.parseReleaseManifest(fetchText(manifestUrl, requestHeaders))
        val valid = parsed.filter { FeatureCodec.validate(it) == null }
        val result = FeatureCodec.computeUpdates(valid, FeatureRegistry.all(context), currentAppVersion())
            .copy(releaseName = releaseName)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putLong(KEY_LAST_CHECKED, System.currentTimeMillis()).apply()
        return result
    }

    fun currentAppVersion(): String = try {
        JarvisApp.instance.packageManager.getPackageInfo(JarvisApp.instance.packageName, 0).versionName ?: "0.0.0"
    } catch (_: Exception) { "0.0.0" }

    private fun fetchText(url: String, requestHeaders: Map<String, String>): String {
        val builder = Request.Builder().url(url)
        requestHeaders.forEach { (name, value) -> builder.header(name, value) }
        client.newCall(builder.build()).execute().use { response ->
            if (response.code == 404)
                throw UpdateCheckException("Not found (404). Check the repository name; a private repo needs a token.")
            if (response.code == 401)
                throw UpdateCheckException("GitHub rejected the saved token (401). Re-enter it in Runtime Feature Updates.")
            if (!response.isSuccessful) throw UpdateCheckException("GitHub returned HTTP " + response.code + ".")
            return response.body?.string() ?: throw UpdateCheckException("Empty response from GitHub.")
        }
    }

    private fun fetchJsonArray(url: String, requestHeaders: Map<String, String>): org.json.JSONArray {
        val body = fetchText(url, requestHeaders)
        return runCatching { org.json.JSONArray(body) }
            .getOrElse { throw UpdateCheckException("GitHub returned an unexpected response.") }
    }

    private fun fetchJson(url: String, requestHeaders: Map<String, String>): JSONObject {
        val body = fetchText(url, requestHeaders)
        return runCatching { JSONObject(body) }
            .getOrElse { throw UpdateCheckException("GitHub returned an unexpected response.") }
    }
}
