package com.jarvis.assistant.backup

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

/**
 * DEV STYLE: settings backup and restore.
 * Only personalisation and feature switches are stored. API keys, GitHub/Vercel tokens,
 * the email app password, the ImgBB key and the creator key are never written out.
 */
object BackupManager {

    private const val PREFS_NAME = "jarvis_prefs"
    private const val FORMAT = 1
    private const val APP_TAG = "jarvis"

    /** Every switch, style and personal detail that is safe to move to another install. */
    private val allowedKeys = setOf(
        "key_model",
        "key_voice",
        "key_personality",
        "key_user_name",
        "key_user_birthday",
        "key_user_location",
        "key_user_age",
        "key_daily_routine",
        "key_favorite_song",
        "key_favorite_app",
        "key_language",
        "key_wake_word",
        "key_app_theme",
        "key_custom_theme_color",
        "key_orb_style",
        "key_orb_animation",
        "key_orb_speed",
        "key_orb_glow",
        "key_orb_scale",
        "key_orb_hue",
        "key_orb_particles",
        "key_offline_replies",
        "key_silent_execution_mode",
        "key_edge_lighting_enabled",
        "key_voice_overlay_enabled",
        "key_voice_overlay_keyboard_enabled",
        "key_voice_overlay_auto_listen",
        "key_mic_muted",
        "key_adult_education_enabled",
        "key_gemini_onboarding_done",
        "key_notification_excluded_apps",
        "key_notification_ai_consent",
        "key_site_agents",
        "key_update_check_url"
    )

    fun export(context: Context, uri: Uri): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val values = JSONObject()
        for (key in allowedKeys) {
            when (val value = prefs.all[key]) {
                null -> continue
                is String -> values.put(key, value)
                is Boolean -> values.put(key, value)
                is Int -> values.put(key, value)
                is Long -> values.put(key, value)
                is Float -> values.put(key, value.toDouble())
                is Set<*> -> values.put(key, JSONArray(value.filterNotNull()))
                else -> continue
            }
        }
        val root = JSONObject().apply {
            put("format", FORMAT)
            put("app", APP_TAG)
            put("created", System.currentTimeMillis())
            put("values", values)
        }
        val stream = context.contentResolver.openOutputStream(uri)
            ?: error("Could not open the chosen file for writing.")
        stream.use { it.write(root.toString(2).toByteArray(Charsets.UTF_8)) }
        return values.length()
    }

    fun restore(context: Context, uri: Uri): Int {
        val raw = context.contentResolver.openInputStream(uri)
            ?.use { it.readBytes().toString(Charsets.UTF_8) }
            ?: error("Could not read the chosen backup file.")
        val root = runCatching { JSONObject(raw) }
            .getOrElse { error("This file is not a JARVIS backup.") }
        if (root.optInt("format") != FORMAT || root.optString("app") != APP_TAG) {
            error("This file is not a JARVIS backup.")
        }
        val values = root.optJSONObject("values") ?: error("The backup has no settings inside it.")
        val editor = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
        var restored = 0
        for (key in values.keys()) {
            if (key !in allowedKeys) continue
            when (val incoming = values.opt(key)) {
                is String -> editor.putString(key, incoming)
                is Boolean -> editor.putBoolean(key, incoming)
                is Int -> editor.putInt(key, incoming)
                is Long -> editor.putLong(key, incoming)
                is Double -> editor.putFloat(key, incoming.toFloat())
                is JSONArray -> {
                    val set = mutableSetOf<String>()
                    for (i in 0 until incoming.length()) {
                        incoming.optString(i).takeIf { it.isNotBlank() }?.let { set.add(it) }
                    }
                    editor.putStringSet(key, set)
                }
                else -> continue
            }
            restored++
        }
        if (!editor.commit()) error("Could not save the restored settings on this device.")
        return restored
    }
}