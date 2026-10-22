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
        val context = applicationContext
        if (!GmailStore.isConnected(context) || !GmailStore.autoReplyEnabled(context)) return Result.success()
        return try {
            GmailStore.recordScan(context, "Checking inbox...")
            val unread = GmailClient.fetchUnread(context, 10)
            var sent = 0
            for (mail in unread) {
                if (!GmailStore.autoReplyEnabled(context)) break
                if (GmailStore.isHandled(context, mail.messageId)) continue
                if (!GmailReplyPolicy.allows(mail, GmailStore.email(context), GmailStore.keywords(context))) {
                    GmailStore.markHandled(context, mail.messageId)
                    continue
                }
                if (!GmailStore.rateLimitAllows(context)) break
                val body = GmailStore.template(context).replace("{name}", mail.fromName)
                GmailClient.send(context, mail.fromEmail, "Re: " + mail.subject, body, mail.messageId)
                GmailStore.markHandled(context, mail.messageId)
                GmailStore.recordAutoReply(context)
                sent++
                GmailClient.markSeen(context, mail.uid)
            }
            GmailStore.recordScan(context, "Inbox checked — $sent auto reply sent")
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val status = if (e is javax.mail.AuthenticationFailedException) "Gmail rejected the sign-in. Check the address and 16-character App Password."
                else "Inbox check failed. Check internet and use Test Gmail for connection details."
            GmailStore.recordScan(context, status)
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
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
