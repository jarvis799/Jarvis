package com.jarvis.assistant.email

import android.content.Context
import androidx.work.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.TimeUnit

/** Device-local background scans. Android can delay the 15-minute schedule. */
class GmailAutoReplyWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = scanLock.withLock { scanInbox() }

    private suspend fun scanInbox(): Result {
        val r = GmailAutoReplyScanner.scan(applicationContext)
        if (r.retry && runAttemptCount < 3) return Result.retry()
        return Result.success()
    }

    companion object {
        private const val UNIQUE_WORK = "jarvis_gmail_auto_reply_247"
        const val IMMEDIATE_WORK = "jarvis_gmail_auto_reply_now"
        private val scanLock = Mutex()
        private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<GmailAutoReplyWorker>(15, TimeUnit.MINUTES)
                .setConstraints(network)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(UNIQUE_WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
            scanNow(context)
        }

        fun scanNow(context: Context) {
            if (!GmailStore.isConnected(context) || !GmailStore.autoReplyEnabled(context)) return
            val request = OneTimeWorkRequestBuilder<GmailAutoReplyWorker>().setConstraints(network)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build()
            WorkManager.getInstance(context).enqueueUniqueWork(IMMEDIATE_WORK, ExistingWorkPolicy.KEEP, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK)
            WorkManager.getInstance(context).cancelUniqueWork(IMMEDIATE_WORK)
        }
    }
}
