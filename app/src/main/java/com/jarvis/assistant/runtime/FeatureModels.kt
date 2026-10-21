package com.jarvis.assistant.runtime

import org.json.JSONArray
import org.json.JSONObject

/**
 * Runtime feature packages delivered from GitHub Releases. A feature is data -
 * a command pack, a prompt pack or a sandboxed HTML mini-tool - never
 * executable Kotlin/DEX code, so it installs without a new APK.
 */
data class ReleaseFeature(
    val id: String,
    val name: String,
    val version: String,
    val description: String,
    val minimumAppVersion: String,
    val downloadUrl: String,
    val sha256: String,
    val featureType: String,
    val changelog: String,
    val downloadSize: Long,
    val entry: String
)

data class InstalledFeature(
    val id: String,
    val name: String,
    val version: String,
    val featureType: String,
    val description: String,
    val entry: String,
    val enabled: Boolean,
    val installedAt: Long,
    val previousVersion: String?
)

data class UpdateCheckResult(
    val available: List<ReleaseFeature>,
    val requiresAppUpdate: List<ReleaseFeature>,
    val releaseName: String,
    val checkedAt: Long
)

object FeatureCodec {

    const val PACKAGE_FORMAT_VERSION = 1
    val FEATURE_TYPES = setOf("commands", "web_tool", "prompt_pack")

    private val ALLOWED_HOSTS = setOf(
        "github.com", "objects.githubusercontent.com", "release-assets.githubusercontent.com"
    )
    private val ID = Regex("[a-z0-9][a-z0-9-]{1,39}")
    private val VERSION = Regex("[0-9]+([.][0-9]+){0,3}")
    private val SHA256 = Regex("[a-fA-F0-9]{64}")

    /** Parses the release manifest: a bare array or {"format_version":1,"features":[...]}. */
    fun parseReleaseManifest(jsonText: String): List<ReleaseFeature> {
        val trimmed = jsonText.trim()
        val arr = if (trimmed.startsWith("[")) JSONArray(trimmed)
            else JSONObject(trimmed).optJSONArray("features") ?: JSONArray()
        val out = mutableListOf<ReleaseFeature>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            out += ReleaseFeature(
                id = o.optString("feature_id").trim(),
                name = o.optString("feature_name").trim(),
                version = o.optString("version").trim(),
                description = o.optString("description").trim(),
                minimumAppVersion = o.optString("minimum_app_version").ifBlank { "1.0.0" },
                downloadUrl = o.optString("download_url").trim(),
                sha256 = o.optString("sha256").trim().lowercase(),
                featureType = o.optString("feature_type").trim().lowercase(),
                changelog = o.optString("changelog").trim(),
                downloadSize = o.optLong("download_size", 0L),
                entry = o.optString("entry").trim()
            )
        }
        return out
    }

    /** Strict validation - a feature that fails any rule is never offered or installed. */
    fun validate(f: ReleaseFeature): String? {
        if (!ID.matches(f.id)) return "Feature id must be 2-40 chars: lowercase letters, digits or dashes."
        if (f.name.isBlank()) return "Feature name is required."
        if (!VERSION.matches(f.version)) return "Version must look like 1.0.0 for feature " + f.id
        if (!VERSION.matches(f.minimumAppVersion)) return "minimum_app_version must look like 3.3.1 for feature " + f.id
        if (!SHA256.matches(f.sha256)) return "sha256 must be a 64-character hex digest for feature " + f.id
        if (f.featureType !in FEATURE_TYPES) return "Unsupported feature_type '" + f.featureType + "'."
        if (f.featureType == "web_tool" && f.entry.isBlank()) return "web_tool features need an entry page."
        val url = runCatching { java.net.URI(f.downloadUrl) }.getOrNull()
            ?: return "download_url is not a valid URL."
        if (url.scheme != "https" || url.host !in ALLOWED_HOSTS)
            return "download_url must be an https GitHub Releases URL."
        if (f.downloadSize < 0 || f.downloadSize > 50L * 1024 * 1024)
            return "download_size exceeds the 50 MB package limit."
        return null
    }

    /** Semantic comparison: 1.2.10 > 1.2.9 > 1.2 */
    fun compareVersions(a: String, b: String): Int {
        val pa = a.split(".").map { it.toIntOrNull() ?: 0 }
        val pb = b.split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(pa.size, pb.size)) {
            val x = pa.getOrNull(i) ?: 0
            val y = pb.getOrNull(i) ?: 0
            if (x != y) return x.compareTo(y)
        }
        return 0
    }

    /**
     * Splits the manifest into features to offer (newer than the installed
     * version and compatible with this app) and features that need a newer
     * APK first.
     */
    fun computeUpdates(
        available: List<ReleaseFeature>,
        installed: List<InstalledFeature>,
        currentAppVersion: String
    ): UpdateCheckResult {
        val byId = installed.associateBy { it.id }
        val updates = mutableListOf<ReleaseFeature>()
        val blocked = mutableListOf<ReleaseFeature>()
        for (f in available) {
            val current = byId[f.id]
            if (current != null && compareVersions(f.version, current.version) <= 0) continue
            if (compareVersions(currentAppVersion, f.minimumAppVersion) < 0) blocked += f
            else updates += f
        }
        return UpdateCheckResult(updates, blocked, "", System.currentTimeMillis())
    }
}
