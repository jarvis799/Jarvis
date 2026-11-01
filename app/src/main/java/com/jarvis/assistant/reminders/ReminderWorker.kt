package com.jarvis.assistant.reminders

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.jarvis.assistant.ui.home.MainActivity

class ReminderWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val text = inputData.getString("message") ?: return Result.failure()
        val notificationId = id.hashCode()
        val intent = PendingIntent.getActivity(applicationContext, notificationId,
            Intent(applicationContext, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(applicationContext, ReminderScheduler.CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Jarvis reminder").setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(intent).setAutoCancel(true).build()
        NotificationManagerCompat.from(applicationContext).notify(notificationId, notification)
        return Result.success()
    }
}