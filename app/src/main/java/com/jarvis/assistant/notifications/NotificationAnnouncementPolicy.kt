package com.jarvis.assistant.notifications

import android.content.Context
import com.jarvis.assistant.JarvisApp
import java.util.Calendar

/**
 * Decides whether an incoming notification may be announced.
 *
 * Every switch is user-owned: the master switch, the per-app opt-outs already stored in
 * [AppPreferences.notificationExcludedApps] and the quiet-hours window. The duplicate window is
 * kept in memory only, so message contents are never written to storage by this policy.
 */
object NotificationAnnouncementPolicy {

    private const val STORE = "jarvis_notification_announcement"
    private const val KEY_ENABLED = "announce_enabled"
    private const val KEY_QUIET_ENABLED = "quiet_hours_enabled"
    private const val KEY_QUIET_START = "quiet_start_hour"
    private const val KEY_QUIET_END = "quiet_end_hour"

    private const val DUPLICATE_WINDOW_MS = 45_000L
    private const val MAX_TRACKED = 64

    private val recent = LinkedHashMap<String, Long>()

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(STORE, Context.MODE_PRIVATE)

    fun enabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, value).apply()
    }

    fun quietHoursEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_QUIET_ENABLED, false)

    fun setQuietHoursEnabled(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_QUIET_ENABLED, value).apply()
    }

    /** Quiet hours start hour, 0-23. Defaults to 23:00. */
    fun quietStartHour(context: Context): Int = prefs(context).getInt(KEY_QUIET_START, 23).coerceIn(0, 23)

    /** Quiet hours end hour, 0-23. Defaults to 07:00. */
    fun quietEndHour(context: Context): Int = prefs(context).getInt(KEY_QUIET_END, 7).coerceIn(0, 23)

    fun setQuietHours(context: Context, startHour: Int, endHour: Int) {
        prefs(context).edit()
            .putInt(KEY_QUIET_START, startHour.coerceIn(0, 23))
            .putInt(KEY_QUIET_END, endHour.coerceIn(0, 23))
            .apply()
    }

    fun inQuietHours(context: Context, now: Long = System.currentTimeMillis()): Boolean {
        if (!quietHoursEnabled(context)) return false
        val hour = Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.HOUR_OF_DAY)
        val start = quietStartHour(context)
        val end = quietEndHour(context)
        if (start == end) return false
        return if (start < end) hour in start until end else hour >= start || hour < end
    }

    /** False when the master switch is off, the app is opted out, or quiet hours are running. */
    fun shouldMonitor(context: Context, packageName: String): Boolean {
        if (!enabled(context)) return false
        if (inQuietHours(context)) return false
        val optedOut = runCatching { JarvisApp.instance.preferences.notificationExcludedApps }.getOrDefault(emptySet())
        return packageName !in optedOut
    }

    /** True unless the same app posted this identical content within the duplicate window. */
    fun shouldAnnounce(packageName: String, title: String, text: String, now: Long = System.currentTimeMillis()): Boolean {
        val key = packageName + "|" + title.trim() + "|" + text.trim()
        synchronized(recent) {
            val cutoff = now - DUPLICATE_WINDOW_MS
            val stale = recent.entries.filter { it.value < cutoff }.map { it.key }
            stale.forEach { recent.remove(it) }
            val last = recent[key]
            recent[key] = now
            while (recent.size > MAX_TRACKED) {
                val oldest = recent.keys.firstOrNull() ?: break
                recent.remove(oldest)
            }
            return last == null || last < cutoff
        }
    }

    fun resetDuplicates() {
        synchronized(recent) { recent.clear() }
    }
}
