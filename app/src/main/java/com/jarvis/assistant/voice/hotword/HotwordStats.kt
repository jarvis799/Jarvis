package com.jarvis.assistant.voice.hotword

import android.content.Context
import android.content.SharedPreferences

/**
 * Honest wake-word counters and uptime, kept in their own preference file so the
 * existing AppPreferences contract is untouched.
 */
object HotwordStats {

    const val ENGINE_SOFTWARE = "SOFTWARE • VOSK"
    const val ENGINE_ASSISTANT = "SYSTEM ASSISTANT SESSION"

    private const val PREFS = "jarvis_hotword_stats"
    private const val KEY_ACTIVATIONS = "activations"
    private const val KEY_FALSE = "false_activations"
    private const val KEY_SESSIONS = "standby_sessions"
    private const val KEY_TOTAL_MS = "standby_total_ms"
    private const val KEY_STARTED_AT = "standby_started_at"
    private const val KEY_ENGINE = "engine_label"

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun recordActivation(context: Context) {
        val p = prefs(context)
        p.edit().putInt(KEY_ACTIVATIONS, p.getInt(KEY_ACTIVATIONS, 0) + 1).apply()
    }

    fun activations(context: Context): Int = prefs(context).getInt(KEY_ACTIVATIONS, 0)

    fun recordFalseActivation(context: Context) {
        val p = prefs(context)
        p.edit().putInt(KEY_FALSE, p.getInt(KEY_FALSE, 0) + 1).apply()
    }

    fun falseActivations(context: Context): Int = prefs(context).getInt(KEY_FALSE, 0)

    /** Called when the standby recogniser really starts listening. */
    fun beginStandby(context: Context) {
        val p = prefs(context)
        if (p.getLong(KEY_STARTED_AT, 0L) > 0L) return
        p.edit()
            .putLong(KEY_STARTED_AT, System.currentTimeMillis())
            .putInt(KEY_SESSIONS, p.getInt(KEY_SESSIONS, 0) + 1)
            .apply()
    }

    /** Called when standby listening stops; the elapsed time is added to the total. */
    fun endStandby(context: Context) {
        val p = prefs(context)
        val started = p.getLong(KEY_STARTED_AT, 0L)
        if (started <= 0L) return
        val elapsed = (System.currentTimeMillis() - started).coerceAtLeast(0L)
        p.edit()
            .putLong(KEY_TOTAL_MS, p.getLong(KEY_TOTAL_MS, 0L) + elapsed)
            .putLong(KEY_STARTED_AT, 0L)
            .apply()
    }

    fun standbySessions(context: Context): Int = prefs(context).getInt(KEY_SESSIONS, 0)

    fun standbyMillis(context: Context): Long {
        val p = prefs(context)
        val total = p.getLong(KEY_TOTAL_MS, 0L)
        val started = p.getLong(KEY_STARTED_AT, 0L)
        return if (started > 0L) total + (System.currentTimeMillis() - started).coerceAtLeast(0L) else total
    }

    fun standbyUptime(context: Context): String {
        val minutes = standbyMillis(context) / 60000L
        if (minutes < 1L) return "less than a minute"
        val hours = minutes / 60L
        return if (hours < 1L) "" + minutes + " min" else "" + hours + " h " + (minutes % 60L) + " min"
    }

    fun setEngine(context: Context, label: String) {
        prefs(context).edit().putString(KEY_ENGINE, label).apply()
    }

    fun engine(context: Context): String = prefs(context).getString(KEY_ENGINE, ENGINE_SOFTWARE) ?: ENGINE_SOFTWARE

    fun reset(context: Context) {
        prefs(context).edit().clear().apply()
    }
}
