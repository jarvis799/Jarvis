package com.jarvis.assistant.website

import android.content.Context
import com.jarvis.assistant.BuildConfig
import com.jarvis.assistant.security.CreatorAuthenticationService
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import org.json.JSONObject

/**
 * DEV STYLE: one entry point for every website action.
 *
 * Read actions (health, stats, version, latestRelease, siteContent, config, docs)
 * work for anyone. Admin actions are creator-only and are rejected locally before
 * any network call when the creator pass has not been verified on this phone.
 */
object WebsiteControl {

    private val adminActions = setOf(
        "dashboard", "agentkey", "users", "devices", "setdevicestatus", "releases",
        "saverelease", "publishrelease", "reports", "audit", "saveconfig", "savesite"
    )

    private val publicActions = listOf(
        "health", "docs", "config", "version", "latestRelease", "siteContent", "register",
        "verify", "heartbeat", "report", "trackDownload", "stats", "incrementDownload", "creatorVerify"
    )

    private val aliases = mapOf(
        "servicestatus" to "health", "status" to "health", "sitehealth" to "health", "healthcheck" to "health",
        "sitestats" to "stats", "statistics" to "stats", "downloadcount" to "stats", "downloads" to "stats",
        "downloadstats" to "stats", "updatecheck" to "version", "appversion" to "version",
        "release" to "latestRelease", "newrelease" to "latestRelease", "latestrelease" to "latestRelease",
        "content" to "siteContent", "websitecontent" to "siteContent", "endpoints" to "docs", "apidocs" to "docs",
        "devicelist" to "devices", "userlist" to "users", "reportlist" to "reports", "auditlog" to "audit",
        "publish" to "publishRelease", "releaselist" to "releases", "settings" to "config", "blocks" to "reports"
    )

    fun resolve(raw: String): String? {
        val key = raw.trim().lowercase().replace(Regex("[^a-z]"), "")
        if (key.isBlank()) return null
        (publicActions + adminActions + "trackdownload").firstOrNull { it.lowercase() == key }?.let { return it }
        return aliases[key]
    }

    suspend fun run(context: Context, actionRaw: String, params: JSONObject = JSONObject()): JSONObject =
        withContext(Dispatchers.IO) {
            val action = resolve(actionRaw) ?: return@withContext fail(
                "Unknown website action. Public: health, docs, config, version, latestRelease, " +
                    "siteContent, stats, register, verify, heartbeat, report, incrementDownload. " +
                    "Creator only: creatorVerify, dashboard, users, devices, setDeviceStatus, releases, " +
                    "saveRelease, publishRelease, reports, audit, saveConfig, saveSite.",
                null
            )

            val payload = JSONObject(params.toString())

            if (action.lowercase() in adminActions) {
                if (!CreatorAuthenticationService.isCreator(context)) {
                    return@withContext fail(
                        "Website admin actions are creator-only. Verify the creator pass first (three taps on the profile photo in Settings).",
                        action
                    )
                }
                val key = WebsiteStore.agentKey(context)
                if (key.isBlank()) {
                    return@withContext fail(
                        "No agent key saved. Open Website control and paste the agent key from the website's Integration page.",
                        action
                    )
                }
                return@withContext report(action, WebsiteApi.adminCall(action, payload, key))
            }

            fillDefaults(context, action, payload)
            report(action, WebsiteApi.publicCall(action, payload))
        }

    private fun fillDefaults(context: Context, action: String, payload: JSONObject) {
        runCatching {
            when (action.lowercase()) {
                "version" -> if (!payload.has("version_code")) {
                    payload.put("version_code", BuildConfig.VERSION_CODE)
                }
                "register" -> {
                    if (!payload.has("installation_id")) payload.put("installation_id", WebsiteStore.installationId(context))
                    if (!payload.has("app_version")) payload.put("app_version", BuildConfig.VERSION_NAME)
                    if (!payload.has("version_code")) payload.put("version_code", BuildConfig.VERSION_CODE)
                    if (!payload.has("device_model")) payload.put("device_model", android.os.Build.MODEL)
                }
                "heartbeat" -> {
                    if (!payload.has("installation_id")) payload.put("installation_id", WebsiteStore.installationId(context))
                    if (!payload.has("device_token")) payload.put("device_token", WebsiteStore.deviceToken(context))
                    if (!payload.has("version_code")) payload.put("version_code", BuildConfig.VERSION_CODE)
                }
            }
        }
    }

    private fun report(action: String, reply: WebsiteApi.Reply): JSONObject = JSONObject().apply {
        put("action", action)
        put("handled", true)
        put("verified", true)
        put("http_ok", reply.ok)
        put("message", shorten(reply.body.ifBlank { reply.error ?: "The website returned an empty body." }))
        if (!reply.ok) put("error", reply.error ?: "Request failed")
        put("system_directive", "This is the live website response. Answer from it, quote the real numbers, and never invent extra fields.")
    }

    private fun fail(message: String, action: String?): JSONObject = JSONObject().apply {
        put("handled", false)
        put("verified", false)
        put("message", message)
        if (action != null) put("action", action)
        put("system_directive", "Tell the user this exact state. Do not claim the website action succeeded.")
    }

    private fun shorten(value: String): String =
        if (value.length <= 1800) value else value.take(1800) + "..."

    // ---------------------------------------------------------------------------------------
    // Typed chat and voice shortcut: "website stats", "download count", "site health", ...
    // ---------------------------------------------------------------------------------------

    private val siteWord = Regex("(?i)\\b(?:website|site|jarvis\\s+web)\\b")
    private val statusWord = Regex(
        "(?i)\\b(?:stats?|statistics|status|health|version|release|downloads?|download\\s+count|devices?|" +
            "users?|installs?|reports?|blocked?|audit|config|docs|dashboard|publish|koyta|koto)\\b"
    )
    private val downloadOnly = Regex("(?i)\\b(?:download\\s+(?:count|stats?)|koyta\\s+download)\\b")

    fun matches(text: String): Boolean {
        val clean = text.trim()
        return (siteWord.containsMatchIn(clean) && statusWord.containsMatchIn(clean)) || downloadOnly.containsMatchIn(clean)
    }

    /** Answers a website question in one line, or null when the text is unrelated. */
    suspend fun route(context: Context, text: String): String? {
        if (!matches(text)) return null
        val action = when {
            Regex("(?i)\\b(?:dashboard)\\b").containsMatchIn(text) -> "dashboard"
            Regex("(?i)\\b(?:devices?|installs?)\\b").containsMatchIn(text) -> "devices"
            Regex("(?i)\\b(?:users?)\\b").containsMatchIn(text) -> "users"
            Regex("(?i)\\b(?:publish|deploy)\\b").containsMatchIn(text) -> "publishRelease"
            Regex("(?i)\\b(?:reports?|blocked?)\\b").containsMatchIn(text) -> "reports"
            Regex("(?i)\\b(?:audit|log)\\b").containsMatchIn(text) -> "audit"
            Regex("(?i)\\b(?:release|version|update)\\b").containsMatchIn(text) -> "latestRelease"
            Regex("(?i)\\b(?:download|stats?|statistics|koyta|koto)\\b").containsMatchIn(text) -> "stats"
            Regex("(?i)\\b(?:config|settings)\\b").containsMatchIn(text) -> "config"
            Regex("(?i)\\b(?:docs|endpoints?|api)\\b").containsMatchIn(text) -> "docs"
            else -> "health"
        }
        val result = run(context, action)
        return result.optString("message").ifBlank { "The website did not return anything." }
    }
}
