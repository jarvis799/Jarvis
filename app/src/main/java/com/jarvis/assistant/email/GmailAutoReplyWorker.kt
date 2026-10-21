package com.jarvis.assistant.email

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * DEV STYLE: 24/7 Gmail auto reply. A periodic WorkManager job (shortest safe
 * interval: 15 minutes) scans the unread inbox over IMAP and answers only the
 * emails the configured rules allow. Runs even when the app is closed;
 * WorkManager persists it across reboots. Gmail limits are respected strictly.
 */
class GmailAutoReplyWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        if (!GmailStore.isConnected(context) || !GmailStore.autoReplyEnabled(context)) {
            return Result.success()
        }
        return try {
            val unread = GmailClient.fetchUnread(context, 10)
            for ((gmail, original) in unread) {
                if (GmailStore.isHandled(context, gmail.messageId)) continue
                if (!shouldReply(context, gmail)) {
                    GmailStore.markHandled(context, gmail.messageId)
                    continue
                }
                if (!GmailStore.rateLimitAllows(context)) break
                sendAutoReply(context, gmail, original)
            }
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    private fun shouldReply(context: Context, gmail: GmailMessage): Boolean {
        if (gmail.isBulk) return false
        val address = gmail.fromEmail.lowercase()
        if (address.contains("no-reply") || address.contains("noreply") || address.contains("donotreply")) return false
        if (address == GmailStore.email(context).lowercase()) return false
        val keywords = GmailStore.keywords(context)
        if (keywords.isNotEmpty()) {
            val haystack = (gmail.fromEmail + " " + gmail.fromName + " " + gmail.subject + " " + gmail.body).lowercase()
            if (keywords.none { haystack.contains(it) }) return false
        }
        if (gmail.subject.startsWith("Re:", ignoreCase = true) && gmail.body.contains("Thank you for your email", ignoreCase = true)) return false
        return true
    }

    private suspend fun sendAutoReply(context: Context, gmail: GmailMessage, original: javax.mail.Message) {
        val body = GmailStore.template(context).replace("{name}", gmail.fromName)
        GmailClient.send(context, gmail.fromEmail, "Re: " + gmail.subject, body)
        GmailClient.markSeen(original)
        GmailStore.markHandled(context, gmail.messageId)
        GmailStore.recordAutoReply(context)
    }

    companion object {
        private const val UNIQUE_WORK = "jarvis_gmail_auto_reply_247"

        /** Arms the 24/7 loop. Called the moment credentials are saved. */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<GmailAutoReplyWorker>(15, TimeUnit.MINUTES)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(UNIQUE_WORK, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK)
        }
    }
}