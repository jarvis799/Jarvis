package com.jarvis.assistant.email

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Date
import java.util.Properties
import javax.mail.Authenticator
import javax.mail.BodyPart
import javax.mail.Flags
import javax.mail.Folder
import javax.mail.Message
import javax.mail.MessagingException
import javax.mail.Multipart
import javax.mail.Part
import javax.mail.PasswordAuthentication
import javax.mail.Session
import javax.mail.Transport
import javax.mail.UIDFolder
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage
import javax.mail.search.FlagTerm

data class GmailMessage(
    val uid: Long,
    val fromEmail: String,
    val fromName: String,
    val subject: String,
    val body: String,
    val sentTime: Long,
    val messageId: String,
    val isBulk: Boolean
)

/**
 * DEV STYLE: Gmail over IMAP (read) and SMTP (send) with a Google App Password.
 * Pure SSL: imap.gmail.com:993 and smtp.gmail.com:465. No OAuth, no token server.
 */
object GmailClient {
    private const val IMAP_HOST = "imap.gmail.com"
    private const val SMTP_HOST = "smtp.gmail.com"
    private const val IMAP_PORT = 993
    private const val SMTP_PORT = 465

    private fun imapSession(): Session {
        val props = Properties()
        props["mail.store.protocol"] = "imaps"
        props["mail.imaps.host"] = IMAP_HOST
        props["mail.imaps.port"] = IMAP_PORT.toString()
        props["mail.imaps.ssl.enable"] = "true"
        props["mail.imaps.ssl.checkserveridentity"] = "true"
        props["mail.imaps.peek"] = "true"
        props["mail.imaps.connectiontimeout"] = "20000"
        props["mail.imaps.timeout"] = "20000"
        return Session.getInstance(props)
    }

    private fun smtpSession(email: String, password: String): Session {
        val props = Properties()
        props["mail.transport.protocol"] = "smtps"
        props["mail.smtps.host"] = SMTP_HOST
        props["mail.smtps.port"] = SMTP_PORT.toString()
        props["mail.smtps.auth"] = "true"
        props["mail.smtps.ssl.enable"] = "true"
        props["mail.smtps.ssl.checkserveridentity"] = "true"
        props["mail.smtps.connectiontimeout"] = "20000"
        props["mail.smtps.timeout"] = "20000"
        props["mail.smtps.writetimeout"] = "20000"
        return Session.getInstance(props, object : Authenticator() {
            override fun getPasswordAuthentication(): PasswordAuthentication = PasswordAuthentication(email, password)
        })
    }

    /** Connects to IMAP and SMTP and reports real mailbox info for the Settings test button. */
    suspend fun testConnection(context: Context): String = withContext(Dispatchers.IO) {
        val email = GmailStore.email(context)
        val password = GmailStore.appPassword(context)
        if (email.isBlank() || password.isBlank()) throw IllegalStateException("Save your Gmail address and App Password first.")
        val store = imapSession().getStore("imaps")
        try {
            store.connect(IMAP_HOST, email, password)
            val inbox = store.getFolder("INBOX")
            inbox.open(Folder.READ_ONLY)
            val total = inbox.messageCount
            val unread = inbox.search(FlagTerm(Flags(Flags.Flag.SEEN), false)).size
            inbox.close(false)
            val transport = smtpSession(email, password).getTransport("smtps")
            try {
                transport.connect(SMTP_HOST, SMTP_PORT, email, password)
            } finally {
                runCatching { transport.close() }
            }
            "OK Sir - IMAP connected. INBOX has " + total + " messages (" + unread + " unread). SMTP send channel is ready."
        } catch (e: MessagingException) {
            throw IllegalStateException(if (e.message?.contains("AUTHENTICATION FAILED", true) == true) "Authentication failed. Check the 16-character App Password (Google Account > Security > 2-Step Verification > App passwords)." else "Gmail connection failed: " + (e.message ?: e.javaClass.simpleName))
        } finally {
            runCatching { store.close() }
        }
    }

    /** Newest messages for voice reading and summaries. */
    suspend fun fetchLatest(context: Context, count: Int): List<GmailMessage> = withContext(Dispatchers.IO) {
        val email = GmailStore.email(context)
        val password = GmailStore.appPassword(context)
        if (email.isBlank() || password.isBlank()) throw IllegalStateException("Gmail is not connected yet.")
        val store = imapSession().getStore("imaps")
        try {
            store.connect(IMAP_HOST, email, password)
            val inbox = store.getFolder("INBOX")
            inbox.open(Folder.READ_ONLY)
            val total = inbox.messageCount
            if (total == 0) return@withContext emptyList<GmailMessage>()
            val result = ArrayList<GmailMessage>(count)
            var i = total
            while (i >= 1 && result.size < count) {
                inbox.getMessage(i)?.let { message -> result.add(toGmailMessage(message, includeBody = false)) }
                i--
            }
            inbox.close(false)
            result
        } finally {
            runCatching { store.close() }
        }
    }

    /** Unread inbox messages with full bodies, oldest first, for the 24/7 auto reply worker. */
    suspend fun fetchUnread(context: Context, limit: Int): List<GmailMessage> = withContext(Dispatchers.IO) {
        val email = GmailStore.email(context)
        val password = GmailStore.appPassword(context)
        if (email.isBlank() || password.isBlank()) return@withContext emptyList()
        val store = imapSession().getStore("imaps")
        try {
            store.connect(IMAP_HOST, email, password)
            val inbox = store.getFolder("INBOX")
            inbox.open(Folder.READ_WRITE)
            val unread = inbox.search(FlagTerm(Flags(Flags.Flag.SEEN), false))
            unread.sortBy { it.messageNumber }
            unread.asSequence()
                .filter { !GmailStore.isHandled(context, messageId(it)) }
                .take(limit.coerceIn(1, 60))
                .map { message -> toGmailMessage(message, includeBody = true) }.toList()
        } finally {
            runCatching { store.close() }
        }
    }

    suspend fun markSeen(context: Context, uid: Long): Unit = withContext(Dispatchers.IO) {
        val store = imapSession().getStore("imaps")
        try {
            store.connect(IMAP_HOST, GmailStore.email(context), GmailStore.appPassword(context))
            val inbox = store.getFolder("INBOX")
            inbox.open(Folder.READ_WRITE)
            (inbox as? UIDFolder)?.getMessageByUID(uid)?.setFlag(Flags.Flag.SEEN, true)
            inbox.close(false)
        } finally { runCatching { store.close() } }
    }

    /** Sends one email over SMTP SSL. */
    suspend fun send(context: Context, to: String, subject: String, body: String, replyToId: String? = null): Unit = withContext(Dispatchers.IO) {
        val email = GmailStore.email(context)
        val password = GmailStore.appPassword(context)
        if (email.isBlank() || password.isBlank()) throw IllegalStateException("Gmail is not connected yet.")
        val session = smtpSession(email, password)
        val message = MimeMessage(session)
        message.setFrom(InternetAddress(email, "Jarvis for Ayan"))
        message.setRecipient(Message.RecipientType.TO, InternetAddress(to))
        message.subject = subject
        message.setText(body, "utf-8")
        message.sentDate = Date()
        if (replyToId != null) {
            message.setHeader("Auto-Submitted", "auto-replied")
            message.setHeader("X-Auto-Response-Suppress", "All")
            message.setHeader("In-Reply-To", replyToId.replace(Regex("[\r\n]"), ""))
            message.setHeader("References", replyToId.replace(Regex("[\r\n]"), ""))
        }
        message.saveChanges()
        val transport = session.getTransport("smtps")
        try {
            transport.connect(SMTP_HOST, SMTP_PORT, email, password)
            transport.sendMessage(message, message.allRecipients)
        } finally {
            runCatching { transport.close() }
        }
    }

    private fun messageId(message: Message): String = message.getHeader("Message-Id")?.firstOrNull()
        ?: "uid-${(message.folder as? UIDFolder)?.uidValidity}-${(message.folder as? UIDFolder)?.getUID(message)}"

    private fun toGmailMessage(message: Message, includeBody: Boolean): GmailMessage {
        val from = (message.from?.firstOrNull() as? InternetAddress)
        val fromEmail = from?.address ?: "unknown@unknown"
        val fromName = from?.personal?.takeIf { it.isNotBlank() } ?: fromEmail.substringBefore("@")
        val subject = runCatching { message.subject ?: "(no subject)" }.getOrDefault("(no subject)")
        val messageId = messageId(message)
        val autoSubmitted = message.getHeader("Auto-Submitted")?.firstOrNull()
        val precedence = message.getHeader("Precedence")?.firstOrNull()?.lowercase()
        val suppressed = message.getHeader("X-Auto-Response-Suppress")?.joinToString(",")?.lowercase().orEmpty()
        val isBulk = message.getHeader("List-Unsubscribe") != null ||
            (autoSubmitted != null && !autoSubmitted.equals("no", true)) ||
            precedence in listOf("bulk", "junk", "list") ||
            suppressed.contains("all") || suppressed.contains("autoreply")
        val fullBody = partText(message)
        val body = if (includeBody) fullBody.take(4000) else fullBody.replace(Regex("\\s+"), " ").take(200)
        val sentTime = runCatching { message.sentDate?.time ?: System.currentTimeMillis() }.getOrDefault(System.currentTimeMillis())
        return GmailMessage(uid = (message.folder as? UIDFolder)?.getUID(message) ?: message.messageNumber.toLong(), fromEmail = fromEmail, fromName = fromName, subject = subject, body = body, sentTime = sentTime, messageId = messageId, isBulk = isBulk)
    }

    private fun partText(part: Part): String {
        return try {
            if (part.isMimeType("text/plain")) {
                part.content as? String ?: ""
            } else if (part.isMimeType("text/html")) {
                (part.content as? String ?: "").replace(Regex("<[^>]*>"), " ").replace(Regex("\\s+"), " ")
            } else if (part.isMimeType("multipart/alternative")) {
                val mp = part.content as Multipart
                var out = ""
                for (i in 0 until mp.count) {
                    val child: BodyPart = mp.getBodyPart(i)
                    if (child.isMimeType("text/plain")) { out = partText(child); break }
                }
                if (out.isBlank()) for (i in 0 until mp.count) { out = partText(mp.getBodyPart(i)); if (out.isNotBlank()) break }
                out
            } else if (part.isMimeType("multipart/*")) {
                val mp = part.content as Multipart
                var out = ""
                for (i in 0 until mp.count) { out = partText(mp.getBodyPart(i)); if (out.isNotBlank()) break }
                out
            } else {
                ""
            }
        } catch (e: Exception) {
            ""
        }
    }
}