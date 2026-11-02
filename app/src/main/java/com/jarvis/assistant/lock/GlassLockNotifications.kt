package com.jarvis.assistant.lock

import android.app.NotificationManager
import android.content.Context

/**
 * The lock screen used to sit in the notification shade as a permanent "Jarvis Liquid Glass"
 * card. It is opened from Settings > Lock screen instead, so nothing is posted here any more;
 * only a card left behind by an older install is cleared.
 */
object GlassLockNotifications {
    private const val ID = 1804

    fun show(context: Context) = cancel(context)

    fun cancel(context: Context) {
        context.getSystemService(NotificationManager::class.java)?.let {
            it.cancel("jarvis_glass_lock", ID)
            it.cancel(ID)
            runCatching { it.deleteNotificationChannel("jarvis_glass_lock") }
        }
    }

    fun attach(context: Context) = cancel(context)

    fun detach(context: Context) = Unit
}