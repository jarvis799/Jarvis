package com.jarvis.assistant.runtime

import android.content.Context
import com.jarvis.assistant.JarvisApp
import com.jarvis.assistant.data.preferences.GithubPreferences
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Talks to GitHub Releases: reads the feature manifest from a published
 * release of the configured repository and decides which features are new,
 * updated or blocked behind an app update. Public repositories need no token;
 * a private repository uses the token the user stores in Runtime Feature
 * Updates (encrypted, on-device, never hardcoded).
 */
object RuntimeUpdateManager {

    const val MANIFEST_ASSET = "feature-manifest.json"
    private const val DEFAULT_REPO = "jarvis799/Jarvis"
    private const val PREFS = "runtime_feature_updates"
    private const val KEY_LAST_CHECKED = "last_checked"
    private const val PER_PAGE = 50
    private const val MAX_PAGES = 2

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    /** The update source is the official repository - nothing to configure here. */
    fun repo(context: Context): String = DEFAULT_REPO
    /**
     * Only the owner's own token is ever sent. A placeholder value here made every
     * check fail with 401, which is why feature updates never arrived.
     */
    fun token(context: Context): String = GithubPreferences(context).token.trim()

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
        // APK builds are published to this repository, so the newest release is
        // usually an APK release whose manifest lists no packs. GitHub also does
        // not always list releases in publication order, so every candidate is
        // tried newest-publication first and a manifest is used only when it
        // actually carries valid feature packs.
        val candidates = manifestReleases(repo, requestHeaders)
        for (candidate in candidates) {
            val parsed = FeatureCodec.parseReleaseManifest(fetchText(candidate.url, requestHeaders))
            val valid = parsed.filter { FeatureCodec.validate(it) == null }
            if (valid.isEmpty()) continue
            val result = FeatureCodec.computeUpdates(valid, FeatureRegistry.all(context), currentAppVersion())
                .copy(releaseName = candidate.name)
            markChecked(context)
            return result
        }
        // Releases that ship only the APK are normal: no feature packs simply
        // means there is nothing new, never a failed update check.
        markChecked(context)
        return UpdateCheckResult(emptyList(), emptyList(), "No feature packs published yet", System.currentTimeMillis())
    }

    private class ManifestRelease(val url: String, val name: String, val publishedAt: String)

    /** Manifest asset URLs from published releases, most recently published first. */
    private fun manifestReleases(
        repo: String,
        requestHeaders: Map<String, String>
    ): List<ManifestRelease> {
        val found = mutableListOf<ManifestRelease>()
        for (page in 1..MAX_PAGES) {
            val releases = fetchJsonArray(
                "https://api.github.com/repos/" + repo + "/releases?per_page=" + PER_PAGE + "&page=" + page,
                requestHeaders
            )
            if (releases.length() == 0) break
            for (i in 0 until releases.length()) {
                val release = releases.optJSONObject(i) ?: continue
                if (release.optBoolean("draft") || release.optBoolean("prerelease")) continue
                val assets = release.optJSONArray("assets") ?: continue
                for (j in 0 until assets.length()) {
                    val asset = assets.optJSONObject(j) ?: continue
                    if (asset.optString("name") != MANIFEST_ASSET) continue
                    val url = asset.optString("browser_download_url")
                    if (url.isBlank()) continue
                    found += ManifestRelease(
                        url = url,
                        name = release.optString("name", ""),
                        publishedAt = release.optString("published_at", release.optString("created_at", ""))
                    )
                    break
                }
            }
        }
        // ISO-8601 UTC timestamps sort chronologically as text.
        return found.sortedByDescending { it.publishedAt }
    }

    private fun markChecked(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putLong(KEY_LAST_CHECKED, System.currentTimeMillis()).apply()
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