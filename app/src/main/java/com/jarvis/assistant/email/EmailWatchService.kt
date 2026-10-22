package com.jarvis.assistant.email

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class EmailWatchService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                runCatching { getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID) }
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_CHECK_NOW -> {
                startForeground(NOTIFICATION_ID, notification("Checking inbox now"))
                scope.launch { GmailAutoReplyScanner.scan(applicationContext); updateNotification(lastStatus()) }
            }
            else -> {
                startForeground(NOTIFICATION_ID, notification("Watching inbox — auto reply ON"))
                if (!polling) { polling = true; scope.launch { loop() } }
            }
        }
        return START_STICKY
    }

    private suspend fun loop() {
        while (isActive) {
            if (GmailStore.isConnected(this) && GmailStore.autoReplyEnabled(this)) {
                GmailAutoReplyScanner.scan(this)
                updateNotification(lastStatus())
            }
            delay(POLL_INTERVAL_MS)
        }
    }

    private fun lastStatus(): String = GmailStore.scanStatus(this).substringAfter(" — ", "Watching inbox")

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Jarvis email monitoring", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun notification(text: String): Notification = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_dialog_email)
        .setContentTitle("Jarvis · Gmail Auto Reply")
        .setContentText(text)
        .setOngoing(true)
        .setCategory(NotificationCompat.CATEGORY_SERVICE)
        .build()

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(text))
    }

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() { polling = false; scope.cancel(); super.onDestroy() }

    companion object {
        const val ACTION_START = "com.jarvis.assistant.email.START"
        const val ACTION_CHECK_NOW = "com.jarvis.assistant.email.CHECK_NOW"
        const val ACTION_STOP = "com.jarvis.assistant.email.STOP"
        private const val CHANNEL_ID = "jarvis_email_watch"
        private const val NOTIFICATION_ID = 3061
        private const val POLL_INTERVAL_MS = 5 * 60 * 1000L
        @Volatile private var polling = false

        fun start(context: Context) {
            val intent = Intent(context, EmailWatchService::class.java).setAction(ACTION_START)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent) else context.startService(intent)
        }
        fun checkNow(context: Context) {
            val intent = Intent(context, EmailWatchService::class.java).setAction(ACTION_CHECK_NOW)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent) else context.startService(intent)
        }
        fun stop(context: Context) {
            runCatching { context.startService(Intent(context, EmailWatchService::class.java).setAction(ACTION_STOP)) }
        }
    }
}
