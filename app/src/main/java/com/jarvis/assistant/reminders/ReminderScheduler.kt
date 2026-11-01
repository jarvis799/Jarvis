package com.jarvis.assistant.reminders

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Durable, device-local reminders. WorkManager restores them after process death and reboot. */
object ReminderScheduler {
    const val CHANNEL = "jarvis_reminders"
    fun schedule(context: Context, time: LocalDateTime, message: String): String {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            return "Reminder save hoyni: Jarvis notifications blocked. Android settings e notifications allow korun."
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Jarvis reminders", NotificationManager.IMPORTANCE_HIGH))
        if (manager.getNotificationChannel(CHANNEL)?.importance == NotificationManager.IMPORTANCE_NONE) {
            return "Reminder save hoyni: Jarvis reminder notification channel blocked. Android notification settings e allow korun."
        }
        val delay = time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() - System.currentTimeMillis()
        if (delay <= 0) return "Reminder time already passed. Notun time bolun."
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInputData(Data.Builder().putString("message", message).build())
            .setInitialDelay(delay, TimeUnit.MILLISECONDS).build()
        WorkManager.getInstance(context).enqueue(request).result.get()
        return "Reminder saved: ${time.format(DateTimeFormatter.ofPattern("dd MMM, h:mm a", Locale.US))}. Android battery restrictions e notification kichuta deri hote pare; exact alarm noy."
    }
}