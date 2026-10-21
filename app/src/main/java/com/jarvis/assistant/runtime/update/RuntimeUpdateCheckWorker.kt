package com.jarvis.assistant.runtime.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.jarvis.assistant.BuildConfig
import com.jarvis.assistant.R

/** Periodic background discovery of signed runtime updates. When a verified
 *  update is available it posts a low-importance notification that opens the
 *  Update Center. It never downloads or installs automatically - the user
 *  confirms in the UI - so nothing runs until it is signature-verified AND
 *  user-approved. */
class RuntimeUpdateCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (BuildConfig.RUNTIME_MANIFEST_URL.isBlank()) return Result.success()
        val ctx = applicationContext
        val current = ModuleStore.activeRuntimeVersion(ctx)
        val appVer = try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "1.0.0" }
                     catch (_: Exception) { "1.0.0" }
        return when (val r = UpdateDiscovery.discover(current, appVer, Build.VERSION.SDK_INT)) {
            is UpdateDiscovery.Result.Available -> { notify(ctx, r.manifest); Result.success() }
            else -> Result.success()
        }
    }

    private fun notify(ctx: Context, manifest: UpdateManifest) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            nm.getNotificationChannel(CHANNEL) ?: nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Runtime updates", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val pi = PendingIntent.getActivity(
            ctx, 0,
            Intent(ctx, RuntimeUpdateCenterActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        nm.notify(NOTIF_ID, NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Runtime " + manifest.runtimeVersion + " available")
            .setContentText(manifest.releaseNotes.take(80))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build())
    }

    companion object {
        private const val CHANNEL = "jarvis_runtime_updates"
        private const val NOTIF_ID = 4201
        const val UNIQUE_WORK = "jarvis_runtime_update_check"
    }
}
