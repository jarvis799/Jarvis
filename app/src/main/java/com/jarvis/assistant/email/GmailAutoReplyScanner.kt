package com.jarvis.assistant.email

import android.content.Context
import kotlinx.coroutines.CancellationException

object GmailAutoReplyScanner {

    data class ScanResult(val sent: Int, val status: String, val retry: Boolean)

    suspend fun scan(context: Context): ScanResult {
        if (!GmailStore.isConnected(context) || !GmailStore.autoReplyEnabled(context)) {
            return ScanResult(0, "Auto reply is off", false)
        }
        return try {
            GmailStore.recordScan(context, "Checking inbox...")
            val unread = GmailClient.fetchUnread(context, 50)
            var sent = 0
            val aiEnabled = GmailStore.aiReplyEnabled(context)
            val apiKey = GmailStore.geminiApiKey(context)
            val signature = GmailStore.replySignature(context)
            for (mail in unread) {
                if (!GmailStore.autoReplyEnabled(context)) break
                if (GmailStore.isHandled(context, mail.messageId)) continue
                if (!GmailReplyPolicy.allows(mail, GmailStore.email(context), GmailStore.keywords(context))) {
                    GmailStore.markHandled(context, mail.messageId)
                    continue
                }
                if (!GmailStore.rateLimitAllows(context)) break
                val plainTemplate = GmailStore.template(context).replace("{name}", mail.fromName)
                val replyBody = if (aiEnabled) {
                    try {
                        val ai = GeminiReplyEngine.createReply(apiKey, mail.fromEmail, mail.subject, mail.body, signature)
                        if (ai == null || ai == "SKIP_REPLY") {
                            GmailStore.markHandled(context, mail.messageId)
                            continue
                        }
                        ai
                    } catch (e: Exception) {
                        plainTemplate
                    }
                } else {
                    plainTemplate
                }
                val replySubject = "Re: " + mail.subject
                // HTML design is optional: when it is on, the same reply travels as an
                // HTML card with a plain-text fallback, so every mail client can read it.
                if (GmailStore.htmlReplyEnabled(context)) {
                    val html = HtmlReplyTemplate.render(
                        GmailStore.htmlTemplate(context),
                        mail.fromName,
                        mail.fromEmail,
                        mail.subject,
                        replyBody
                    )
                    GmailClient.sendHtml(context, mail.fromEmail, replySubject, html, mail.messageId)
                } else {
                    GmailClient.send(context, mail.fromEmail, replySubject, replyBody, mail.messageId)
                }
                GmailStore.markHandled(context, mail.messageId)
                GmailStore.recordAutoReply(context)
                sent++
                GmailClient.markSeen(context, mail.uid)
            }
            val status = "Inbox checked — " + sent + " auto reply sent"
            GmailStore.recordScan(context, status)
            ScanResult(sent, status, false)
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
}