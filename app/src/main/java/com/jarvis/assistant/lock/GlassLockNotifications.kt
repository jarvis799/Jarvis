package com.jarvis.assistant.lock

import android.app.*
import android.content.*
import androidx.core.content.ContextCompat

/** Uses a normal user-opened notification, not a fake call/alarm full-screen intent. */
object GlassLockNotifications {
    private const val ID = 1804
    fun show(context: Context) {
        if (!VoiceUnlockStore(context).lockEnabled()) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(NotificationChannel("jarvis_glass_lock", "Jarvis lock screen", NotificationManager.IMPORTANCE_LOW))
        if (!manager.areNotificationsEnabled()) return
        val open = PendingIntent.getActivity(context, ID, Intent(context, GlassLockActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        manager.notify(ID, Notification.Builder(context, "jarvis_glass_lock")
            .setSmallIcon(android.R.drawable.ic_lock_lock).setContentTitle("Jarvis Liquid Glass")
            .setContentText("Tap to open your lock screen. Android authentication stays enabled.")
            .setVisibility(Notification.VISIBILITY_PUBLIC).setContentIntent(open).setOngoing(true).build())
    }
    fun cancel(context: Context) { context.getSystemService(NotificationManager::class.java)?.cancel(ID) }
    private var receiver: BroadcastReceiver? = null
    fun detach(context: Context) { receiver?.let { context.unregisterReceiver(it) }; receiver = null }
    fun attach(context: Context) {
        if (receiver != null) return
        val listener = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) { if (intent.action == Intent.ACTION_SCREEN_ON) show(c) }
        }
        ContextCompat.registerReceiver(context, listener, IntentFilter(Intent.ACTION_SCREEN_ON), ContextCompat.RECEIVER_NOT_EXPORTED)
        receiver = listener
        show(context)
    }
}