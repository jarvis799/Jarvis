package com.jarvis.assistant.email

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.atomic.AtomicInteger

object GmailAutoReplyScanner {

    data class ScanResult(val sent: Int, val status: String, val retry: Boolean)

    /** One scan at a time, whichever component asked for it (watcher or background worker). */
    private val scanLock = Mutex()

    private const val MAX_PER_SCAN = 12
    private const val PARALLEL = 4

    suspend fun scan(context: Context): ScanResult = scanLock.withLock { runScan(context) }

    private suspend fun runScan(context: Context): ScanResult {
        if (!GmailStore.isConnected(context) || !GmailStore.autoReplyEnabled(context)) {
            return ScanResult(0, "Auto reply is off", false)
        }
        return try {
            GmailStore.recordScan(context, "Checking inbox...")
            val ownEmail = GmailStore.email(context)
            val keywords = GmailStore.keywords(context)

            // Unread mail first, then the recent backlog, so older unanswered mail is caught too.
            val unread = runCatching { GmailClient.fetchUnread(context, 30) }.getOrDefault(emptyList())
            val backlog = runCatching { GmailClient.fetchRecent(context, MAX_PER_SCAN) }.getOrDefault(emptyList())
            val candidates = (unread + backlog)
                .distinctBy { it.messageId }
                .filter { !GmailStore.isHandled(context, it.messageId) }
                .sortedByDescending { it.sentTime }
                .take(MAX_PER_SCAN)

            val queue = ArrayList<GmailMessage>()
            for (mail in candidates) {
                if (GmailReplyPolicy.allows(mail, ownEmail, keywords)) queue.add(mail)
                else GmailStore.markHandled(context, mail.messageId)
            }

            val sent = AtomicInteger(0)
            val replied = StringBuilder()
            val aiEnabled = GmailStore.aiReplyEnabled(context)
            val apiKey = GmailStore.geminiApiKey(context)
            val signature = GmailStore.replySignature(context)
            val htmlEnabled = GmailStore.htmlReplyEnabled(context)
            val htmlTemplate = GmailStore.htmlTemplate(context)
            val gate = Semaphore(PARALLEL)
            val rateGate = Mutex()

            coroutineScope {
                queue.map { mail ->
                    async(Dispatchers.IO) {
                        gate.withPermit {
                            if (!GmailStore.autoReplyEnabled(context)) return@withPermit
                            // Reserve the send slot before doing any work, so parallel replies
                            // still respect the hourly / daily / gap limits.
                            val reserved = rateGate.withLock {
                                val allowed = GmailStore.rateLimitAllows(context)
                                if (allowed) GmailStore.recordAutoReply(context)
                                allowed
                            }
                            if (!reserved) return@withPermit
                            val ok = runCatching { replyTo(context, mail, aiEnabled, apiKey, signature, htmlEnabled, htmlTemplate) }.getOrDefault(false)
                            if (ok) {
                                sent.incrementAndGet()
                                synchronized(replied) {
                                    if (replied.isNotEmpty()) replied.append(" | ")
                                    replied.append(mail.fromName.ifBlank { mail.fromEmail })
                                        .append(" - ").append(mail.subject.take(60))
                                }
                            }
                        }
                    }
                }.awaitAll()
            }

            val total = sent.get()
            val status = if (total == 0) "Inbox checked - no new mail needed a reply"
            else "Inbox checked - " + total + " auto reply sent: " + replied.toString()
            GmailStore.recordScan(context, status)
            ScanResult(total, status, false)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val status = if (e is javax.mail.AuthenticationFailedException)
                "Gmail rejected the sign-in. Check the address and 16-character App Password."
            else "Inbox check failed. Check internet and use Test Gmail for connection details."
            GmailStore.recordScan(context, status)
            ScanResult(0, status, true)
        }
    }

    private suspend fun replyTo(
        context: Context,
        mail: GmailMessage,
        aiEnabled: Boolean,
        apiKey: String,
        signature: String,
        htmlEnabled: Boolean,
        htmlTemplate: String
    ): Boolean {
        val plainTemplate = GmailStore.template(context).replace("{name}", HtmlReplyTemplate.firstName(mail.fromName))
        val replyBody = if (aiEnabled) {
            val ai = try {
                GeminiReplyEngine.createReply(apiKey, mail.fromEmail, mail.subject, mail.body, signature)
            } catch (e: Exception) {
                null
            }
            if (ai == null || ai == "SKIP_REPLY") {
                GmailStore.markHandled(context, mail.messageId)
                return false
            }
            HtmlReplyTemplate.stripGreeting(ai)
        } else {
            plainTemplate
        }
        val replySubject = "Re: " + mail.subject
        // HTML design is optional: when it is on, the same reply travels as an
        // HTML card with a plain-text fallback, so every mail client can read it.
        if (htmlEnabled) {
            val html = HtmlReplyTemplate.render(htmlTemplate, mail.fromName, mail.fromEmail, mail.subject, replyBody)
            GmailClient.sendHtml(context, mail.fromEmail, replySubject, html, mail.messageId)
        } else {
            GmailClient.send(context, mail.fromEmail, replySubject, HtmlReplyTemplate.ensureGreeting(replyBody, mail.fromName), mail.messageId)
        }
        GmailStore.markHandled(context, mail.messageId)
        GmailClient.markSeen(context, mail.uid)
        return true
    }
}
