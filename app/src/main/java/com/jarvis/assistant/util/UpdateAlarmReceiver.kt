package com.jarvis.assistant.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import com.jarvis.assistant.network.AhonaSync

/**
 * DEV STYLE: fires every 2 hours.
 *  1. re-announces this name + phone to /api/user/create (keeps the backend card fresh)
 *  2. sends a heartbeat when this installation holds a device token
 *  3. re-checks /api/version and notifies when a newer build is live
 */
class UpdateAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        Thread {
            runCatching {
                val installed = context.packageManager
                    .getPackageInfo(context.packageName, 0).versionName ?: "1.0"
                val ownerName = runCatching {
                    com.jarvis.assistant.JarvisApp.instance.preferences.userName
                }.getOrDefault("")

                runCatching { AhonaSync.register(context, ownerName) }
                runCatching { AhonaSync.heartbeat(context, installed) }

                val gate = AhonaSync.gate(context, installed)
                if (gate.forced) showUpdateNotification(context, gate)
            }
            pending.finish()
        }.start()
    }

    private fun showUpdateNotification(context: Context, gate: AhonaSync.Gate) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "jarvis_update"
        nm.createNotificationChannel(
            NotificationChannel(channelId, "JARVIS updates", NotificationManager.IMPORTANCE_HIGH)
        )
        val pi = PendingIntent.getActivity(
            context, 99,
            Intent(Intent.ACTION_VIEW, Uri.parse(gate.resolvedUrl())),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("JARVIS " + gate.latestVersion + " is ready")
            .setContentText(
                gate.message.ifBlank { "New version available — update now, the old version stops working." }
            )
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        nm.notify(42, notification)
    }
}