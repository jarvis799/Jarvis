package com.jarvis.assistant.media

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

/** Holds playback so music keeps running while Jarvis is in the background. */
class MusicPlaybackService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE -> JarvisMusicPlayer.toggle(this)
            ACTION_NEXT -> JarvisMusicPlayer.next(this)
            ACTION_PREVIOUS -> JarvisMusicPlayer.previous(this)
            ACTION_STOP -> JarvisMusicPlayer.stopAll(this)
        }
        if (JarvisMusicPlayer.current != null) {
            ensureChannel()
            try {
                startForeground(NOTIFICATION_ID, buildNotification())
            } catch (_: Exception) {
            }
        } else {
            try {
                stopForeground(true)
            } catch (_: Exception) {
            }
            stopSelf()
        }
        return START_STICKY
    }

    private fun buildNotification(): Notification {
        val track = JarvisMusicPlayer.current
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MusicPlayerActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(track?.title ?: "Jarvis Music")
            .setContentText(track?.artist ?: "")
            .setContentIntent(open)
            .setOnlyAlertOnce(true)
            .setOngoing(JarvisMusicPlayer.isPlaying)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(android.R.drawable.ic_media_previous, "Previous", action(ACTION_PREVIOUS))
            .addAction(
                if (JarvisMusicPlayer.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (JarvisMusicPlayer.isPlaying) "Pause" else "Play",
                action(ACTION_TOGGLE)
            )
            .addAction(android.R.drawable.ic_media_next, "Next", action(ACTION_NEXT))
            .build()
    }

    private fun action(name: String): PendingIntent {
        val intent = Intent(this, MusicPlaybackService::class.java).setAction(name)
        return PendingIntent.getService(this, name.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun ensureChannel() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Music playback", NotificationManager.IMPORTANCE_LOW)
            channel.setShowBadge(false)
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val ACTION_TOGGLE = "com.jarvis.assistant.music.TOGGLE"
        const val ACTION_NEXT = "com.jarvis.assistant.music.NEXT"
        const val ACTION_PREVIOUS = "com.jarvis.assistant.music.PREVIOUS"
        const val ACTION_STOP = "com.jarvis.assistant.music.STOP"
        private const val CHANNEL_ID = "jarvis_music"
        private const val NOTIFICATION_ID = 4210
    }
}
