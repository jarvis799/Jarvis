package com.jarvis.assistant.network

import android.content.Context
import android.os.Build
import com.jarvis.assistant.security.InstallationManager

/**
 * DEV STYLE: the one place the app talks to https://ahona-voice-os.base44.app.
 *
 *  - every app open announces WHO opened (name) and FROM WHICH PHONE (device label) to
 *    POST /api/user/create, so the card exists in the backend database
 *  - every app open asks GET /api/version; a newer build in the panel makes this build
 *    unusable until the user updates (blocking gate)
 *  - a stored device token, when one exists, sends heartbeats and client errors
 *
 * No admin token and no secret is stored in the app.
 */
object AhonaSync {

    private const val PREFS = "ahona_sync_v1"
    private const val KEY_REGISTERED = "registered"
    private const val KEY_PREMIUM = "premium"
    private const val KEY_FORCED = "update_forced"
    private const val KEY_LATEST = "latest_version"
    private const val KEY_URL = "update_url"
    private const val KEY_MESSAGE = "update_message"
    private const val KEY_LAST_SYNC = "last_sync"

    private const val FALLBACK_UPDATE_URL = "https://github.com/arian00001/User-jarvis/releases/latest"

    data class Gate(
        val forced: Boolean,
        val latestVersion: String,
        val updateUrl: String,
        val message: String
    ) {
        fun resolvedUrl(): String = updateUrl.ifBlank { FALLBACK_UPDATE_URL }
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** e.g. "samsung SM-S918B" — the phone the app was opened on. */
    fun deviceLabel(): String {
        val maker = Build.MANUFACTURER.orEmpty().trim()
        val model = Build.MODEL.orEmpty().trim()
        val label = listOf(maker, model).filter { it.isNotBlank() }.joinToString(" ")
        return label.ifBlank { "Android device" }
    }

    /** POST /api/user/create — name + phone + installation land in App users. */
    fun register(context: Context, name: String): Boolean {
        val installationId = runCatching { InstallationManager.installationId(context) }.getOrNull()
        val response = AhonaApi.createUser(
            name = name.trim().ifBlank { "jarvis-user" },
            deviceModel = deviceLabel(),
            installationId = installationId
        )
        val success = response.optBoolean("success", false)
        prefs(context).edit()
            .putBoolean(KEY_REGISTERED, success)
            .putBoolean(KEY_PREMIUM, response.optBoolean("premium", false))
            .putLong(KEY_LAST_SYNC, System.currentTimeMillis())
            .apply()
        return success
    }

    /** GET /api/version — decides whether this build may still be used. */
    fun gate(context: Context, installedVersion: String): Gate {
        val installed = installedVersion.trim()
        val check = AhonaApi.versionCheck(installed)
        val latest = check.latestVersion
        val forced = check.updateAvailable || isNewer(latest, installed)
        val gate = Gate(forced, latest, check.updateUrl, check.message)
        prefs(context).edit()
            .putBoolean(KEY_FORCED, forced)
            .putString(KEY_LATEST, latest)
            .putString(KEY_URL, gate.updateUrl)
            .putString(KEY_MESSAGE, gate.message)
            .apply()
        return gate
    }

    /** POST /api/report/heartbeat — only when this installation already holds a device token. */
    fun heartbeat(
        context: Context,
        installedVersion: String,
        errors: List<AhonaApi.ClientError> = emptyList()
    ): AhonaApi.HeartbeatResult? {
        val token = runCatching { InstallationManager.deviceToken(context) }.getOrNull() ?: return null
        val installationId = InstallationManager.installationId(context)
        return AhonaApi.heartbeat(token, installationId, installedVersion, deviceLabel(), errors)
    }

    fun lastKnownGate(context: Context): Gate = Gate(
        forced = prefs(context).getBoolean(KEY_FORCED, false),
        latestVersion = prefs(context).getString(KEY_LATEST, "").orEmpty(),
        updateUrl = prefs(context).getString(KEY_URL, "").orEmpty(),
        message = prefs(context).getString(KEY_MESSAGE, "").orEmpty()
    )

    fun isPremiumOnServer(context: Context): Boolean = prefs(context).getBoolean(KEY_PREMIUM, false)

    fun isRegistered(context: Context): Boolean = prefs(context).getBoolean(KEY_REGISTERED, false)

    fun lastSyncAt(context: Context): Long = prefs(context).getLong(KEY_LAST_SYNC, 0L)

    private fun isNewer(remote: String, installed: String): Boolean {
        if (remote.isBlank()) return false
        val parse = { value: String ->
            value.trim().removePrefix("v").split(".").map { it.toIntOrNull() ?: 0 }
        }
        val remoteParts = parse(remote)
        val localParts = parse(installed)
        for (index in 0 until maxOf(remoteParts.size, localParts.size)) {
            val remotePart = remoteParts.getOrElse(index) { 0 }
            val localPart = localParts.getOrElse(index) { 0 }
            if (remotePart != localPart) return remotePart > localPart
        }
        return false
    }
}