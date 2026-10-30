package com.jarvis.assistant.personality

import android.content.Context
import android.content.SharedPreferences

/** What Jarvis was doing when the boss left. */
enum class SessionState { IDLE, LISTENING, SPEAKING, THINKING }

/**
 * Remembers when the boss last left the app plus a little context, so Jarvis can greet
 * him with the real elapsed time instead of inventing one.
 *
 * Wall-clock `System.currentTimeMillis()` is used, so midnight, date changes and device
 * restarts are all handled by the platform. A missing / future timestamp yields -1 and
 * the greeter then says nothing about duration.
 */
class SessionMemoryStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun recordSessionEnd(
        topic: String = "",
        state: SessionState = SessionState.IDLE,
        lastRequest: String = ""
    ) {
        prefs.edit()
            .putLong(KEY_LAST_EXIT_AT, System.currentTimeMillis())
            .putString(KEY_LAST_TOPIC, topic.take(280))
            .putString(KEY_LAST_STATE, state.name)
            .putString(KEY_LAST_REQUEST, lastRequest.take(280))
            .apply()
    }

    fun lastExitAt(): Long = prefs.getLong(KEY_LAST_EXIT_AT, -1L)

    fun lastTopic(): String = prefs.getString(KEY_LAST_TOPIC, "").orEmpty()

    fun lastRequest(): String = prefs.getString(KEY_LAST_REQUEST, "").orEmpty()

    fun lastState(): SessionState =
        SessionState.entries.firstOrNull { it.name == prefs.getString(KEY_LAST_STATE, null) }
            ?: SessionState.IDLE

    fun elapsedSinceLastExit(now: Long = System.currentTimeMillis()): Long {
        val last = lastExitAt()
        if (last <= 0L || now <= last) return -1L
        return now - last
    }

    fun clear() = prefs.edit().clear().apply()

    companion object {
        private const val PREF_NAME = "jarvis_session_memory_v1"
        private const val KEY_LAST_EXIT_AT = "last_exit_at"
        private const val KEY_LAST_TOPIC = "last_topic"
        private const val KEY_LAST_STATE = "last_state"
        private const val KEY_LAST_REQUEST = "last_request"
    }
}

/**
 * Builds the welcome-back line. Pure Kotlin (no Android) so the time maths is testable.
 * It asks what the boss was doing instead of pretending to know.
 */
object SessionGreeter {

    private const val SHORT_ABSENCE_MS = 5 * 60 * 1000L

    /** "" for a brief app switch, so switching screens never repeats the greeting. */
    fun greeting(
        elapsedMs: Long,
        topic: String = "",
        language: String = "en",
        addressName: String = "Boss"
    ): String {
        if (elapsedMs < 0) return defaultGreeting(language, addressName)
        if (elapsedMs < SHORT_ABSENCE_MS) return ""

        val duration = formatDuration(elapsedMs)
        val topicLine = if (topic.isBlank()) "" else topicHint(language, topic)

        return when (language.lowercase().take(2)) {
            "bn" -> "Boss, welcome back! প্রায় $duration পর ফিরলেন। এতক্ষণ কী করছিলেন, $addressName?$topicLine"
            "hi" -> "$addressName, welcome back! लगभग $duration बाद लौटे। इतनी देर में क्या कर रहे थे?$topicLine"
            else -> "Welcome back, $addressName! That was about $duration. What were you up to?$topicLine"
        }
    }

    /** When no timestamp exists: greet normally, never fabricate a duration. */
    fun defaultGreeting(language: String = "en", addressName: String = "Boss"): String =
        when (language.lowercase().take(2)) {
            "bn" -> "Boss, আমি এখানেই আছি। বলুন, কী করতে হবে?"
            "hi" -> "$addressName, मैं यहीं हूँ। बताइए, क्या करना है?"
            else -> "I'm here, $addressName. What would you like to do?"
        }

    private fun topicHint(language: String, topic: String): String =
        when (language.lowercase().take(2)) {
            "bn" -> " গতবার আমরা $topic নিয়ে কাজ করছিলাম — সেটাই চালিয়ে যাব?"
            "hi" -> " पिछली बार हम $topic पर काम कर रहे थे — वहीं से शुरू करें?"
            else -> " Last time we were on $topic — shall we continue?"
        }

    /** "1 hour 19 minutes" style, never raw milliseconds. */
    fun formatDuration(ms: Long): String {
        if (ms < 60_000L) return "${(ms / 1000).coerceAtLeast(1)} seconds"
        val totalMinutes = ms / 60_000L
        val days = totalMinutes / (24 * 60)
        val hours = (totalMinutes % (24 * 60)) / 60
        val minutes = totalMinutes % 60
        val parts = mutableListOf<String>()
        if (days > 0) parts += plural(days, "day")
        if (hours > 0) parts += plural(hours, "hour")
        if (minutes > 0 && days == 0L) parts += plural(minutes, "minute")
        return parts.joinToString(" ")
    }

    private fun plural(value: Long, unit: String): String =
        if (value == 1L) "1 $unit" else "$value ${unit}s"
}