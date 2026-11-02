package com.jarvis.assistant.website

import android.content.Context
import java.util.UUID

/** Small on-device store for the website agent key and this install's registration. */
object WebsiteStore {

    private const val PREFS = "jarvis_website"
    private const val KEY_AGENT = "agent_key"
    private const val KEY_INSTALLATION = "installation_id"
    private const val KEY_DEVICE_TOKEN = "device_token"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun agentKey(context: Context): String =
        (prefs(context).getString(KEY_AGENT, "") ?: "").trim()

    fun setAgentKey(context: Context, key: String) {
        prefs(context).edit().putString(KEY_AGENT, key.trim()).apply()
    }

    /** Stable, non-identifying install id; generated once and reused for every API call. */
    fun installationId(context: Context): String {
        val store = prefs(context)
        val existing = store.getString(KEY_INSTALLATION, "") ?: ""
        if (existing.isNotBlank()) return existing
        val created = "and-" + UUID.randomUUID().toString().take(12)
        store.edit().putString(KEY_INSTALLATION, created).apply()
        return created
    }

    fun deviceToken(context: Context): String =
        prefs(context).getString(KEY_DEVICE_TOKEN, "") ?: ""

    fun setDeviceToken(context: Context, token: String) {
        prefs(context).edit().putString(KEY_DEVICE_TOKEN, token.trim()).apply()
    }
}
