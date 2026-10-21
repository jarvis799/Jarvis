package com.jarvis.assistant.devstudio

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.jarvis.assistant.R
import com.jarvis.assistant.ui.devstudio.ProjectStudioActivity

/**
 * Foreground service so a project keeps generating while the user leaves the screen.
 * It stops as soon as the job reaches a final state.
 */
class ProjectJobService : Service() {

    companion object {
        private const val CHANNEL = "jarvis_devstudio"
        private const val NOTIFICATION_ID = 4711
        const val ACTION_START = "com.jarvis.assistant.devstudio.START"
        const val ACTION_CANCEL = "com.jarvis.assistant.devstudio.CANCEL"
        const val EXTRA_JOB_ID = "job_id"

        fun start(context: Context, jobId: String) {
            val intent = Intent(context, ProjectJobService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_JOB_ID, jobId)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
            else context.startService(intent)
        }

        fun cancel(context: Context) {
            context.startService(Intent(context, ProjectJobService::class.java).setAction(ACTION_CANCEL))
        }
    }

    private val runner by lazy { ProjectJobRunner(applicationContext) }
    private lateinit var store: ProjectStore

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        store = ProjectStore(applicationContext)
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            runner.cancel()
            stopSelf()
            return START_NOT_STICKY
        }
        val jobId = intent?.getStringExtra(EXTRA_JOB_ID)
        val job = jobId?.let { store.find(it) }
        if (job == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(NOTIFICATION_ID, notification(job.projectName, job.message.ifBlank { "Preparing" }))
        runner.start(job, onUpdate = { updated ->
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(NOTIFICATION_ID, notification(updated.projectName, updated.stage.label() + " · " + updated.message))
        }, onFinished = { finished ->
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val text = when (finished.stage) {
                ProjectStage.COMPLETED -> finished.projectName + " ready" + (if (finished.zipBytes > 0) " · ZIP " + finished.zipBytes / 1024 + " KB" else "")
                ProjectStage.CANCELLED -> finished.projectName + " cancelled"
                ProjectStage.FAILED -> finished.projectName + " failed: " + finished.error
                else -> finished.projectName + " " + finished.stage.label()
            }
            manager.notify(NOTIFICATION_ID, notification(finished.projectName, text))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) stopForeground(STOP_FOREGROUND_DETACH)
            stopSelf()
        })
        return START_NOT_STICKY
    }

    private fun notification(project: String, text: String) = NotificationCompat.Builder(this, CHANNEL)
        .setSmallIcon(R.drawable.ic_settings_db)
        .setContentTitle("Jarvis Developer Studio · " + project)
        .setContentText(text)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setContentIntent(PendingIntent.getActivity(
            this, 0, Intent(this, ProjectStudioActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        ))
        .build()

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL) == null) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL, "Developer Studio", NotificationManager.IMPORTANCE_LOW))
        }
    }
}
