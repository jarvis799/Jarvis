package com.jarvis.assistant.email

object GmailReplyPolicy {
    fun allows(mail: GmailMessage, ownEmail: String, keywords: List<String>): Boolean {
        if (mail.isBulk) return false
        val address = mail.fromEmail.lowercase()
        if (address == ownEmail.lowercase()) return false
        if (listOf("no-reply", "noreply", "donotreply", "mailer-daemon", "postmaster").any { address.contains(it) }) return false
        if (keywords.isNotEmpty()) {
            val text = "${mail.fromEmail} ${mail.fromName} ${mail.subject} ${mail.body}".lowercase()
            if (keywords.none { text.contains(it.lowercase()) }) return false
        }
        return true
    }
}
