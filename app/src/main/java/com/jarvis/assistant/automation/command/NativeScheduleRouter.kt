package com.jarvis.assistant.automation.command

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Bounded local time parsing. Ambiguous or incomplete times never become guessed alarms. */
object NativeScheduleRouter {
    private val subject = Regex("(?i)\\b(alarm|alam|reminder|remind me)\\b|অ্যালার্ম|এলার্ম|রিমাইন্ডার|মনে করিয়ে")
    private val action = Regex("(?i)\\b(set|create|add|lagao|lagana|dao|diye dao|koro|kor|remind me)\\b|সেট|দাও|লাগাও")
    private val interval = Regex("(?i)(\\d{1,4})\\s*(minutes?|mins?|min|hours?|hrs?|hr|মিনিট|ঘণ্টা|ঘন্টা)")
    private val clock = Regex("(?i)(?:(?:at|for|kal|tomorrow|aj|today|সকাল|বিকাল|রাত)\\s+)?(\\d{1,2})(?::([0-5]\\d))?\\s*(am|pm|a\\.m\\.|p\\.m\\.|ta|টায়|টায়|টা|baje|বাজে)?")

    fun matches(text: String): Boolean = subject.containsMatchIn(text) && action.containsMatchIn(text)

    suspend fun route(context: Context, text: String): String? {
        if (!matches(text)) return null
        val whenAt = parseTime(text) ?: return "Kokhon set korbo? Example: set alarm at 7 am, ba remind me in 10 minutes to drink water."
        val label = text.replace(subject, "").replace(action, "").trim(' ', ',', ':').take(160).ifBlank { "Jarvis reminder" }
        if (Regex("(?i)remind|রিমাইন্ড|মনে করিয়ে").containsMatchIn(text)) {
            return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                com.jarvis.assistant.reminders.ReminderScheduler.schedule(context, whenAt, label)
            }
        }
        val nextClockTime = LocalDateTime.now().let { now ->
            now.toLocalDate().atTime(whenAt.hour, whenAt.minute).let { if (it.isAfter(now)) it else it.plusDays(1) }
        }
        if (whenAt.toLocalDate().isAfter(nextClockTime.toLocalDate())) {
            return "Clock er ei action diye specific future date save verify kora jay na. Reminder hisebe set korte bolun, ba Clock app e date choose korun. Kono alarm save hoyni."
        }
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, whenAt.hour)
            putExtra(AlarmClock.EXTRA_MINUTES, whenAt.minute)
            putExtra(AlarmClock.EXTRA_MESSAGE, label)
            // The system Clock owns saving; show its confirmation instead of claiming an unseen save.
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            "Clock e ${whenAt.format(DateTimeFormatter.ofPattern("h:mm a", Locale.US))} alarm request khulechi. Clock e save/confirm korun; ekhono save hoyeche bole confirm korchi na."
        } catch (_: android.content.ActivityNotFoundException) {
            "Ei phone e alarm set korar Clock app pawa jayni. Kono alarm save hoyni."
        }
    }

    fun parseTime(input: String, now: LocalDateTime = LocalDateTime.now()): LocalDateTime? {
        val text = input.map { c -> if (c in '০'..'৯') '0' + (c - '০') else c }.joinToString("")
        interval.find(text)?.let { match ->
            val count = match.groupValues[1].toLongOrNull() ?: return null
            if (count <= 0 || !Regex("(?i)\\b(in|after|pore|por)\\b|পরে").containsMatchIn(text)) return null
            return now.plusMinutes(count * if (Regex("(?i)hour|hr|ঘ").containsMatchIn(match.groupValues[2])) 60 else 1)
        }
        val match = clock.findAll(text).firstOrNull { it.groupValues[3].isNotBlank() || it.groupValues[2].isNotBlank() } ?: return null
        var hour = match.groupValues[1].toIntOrNull() ?: return null
        val minute = match.groupValues[2].toIntOrNull() ?: 0
        val marker = match.groupValues[3].lowercase(Locale.ROOT)
        val pm = marker.startsWith("p") || Regex("(?i)\\b(evening|night|bikal|raat)\\b|বিকাল|রাত").containsMatchIn(text)
        val am = marker.startsWith("a") || Regex("(?i)\\b(morning|sokal)\\b|সকাল").containsMatchIn(text)
        if ((pm || am) && hour !in 1..12) return null
        if (!pm && !am && marker.isNotBlank() && hour in 1..12) return null
        if (pm || am) hour = hour % 12 + if (pm) 12 else 0
        if (hour !in 0..23) return null
        var target = now.toLocalDate().atTime(hour, minute)
        if (Regex("(?i)\\b(tomorrow|kal)\\b|আগামীকাল").containsMatchIn(text)) target = target.plusDays(1)
        else if (!target.isAfter(now)) target = target.plusDays(1)
        return target
    }
}