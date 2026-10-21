package com.jarvis.assistant.email

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * DEV STYLE: Gmail voice commands - read, summarize, draft and send after
 * confirmation. All network work happens on IO; replies to the user are plain
 * spoken sentences (existing TTS speaks whatever the CommandExecutor returns).
 */
object GmailVoiceCommands {

    data class PendingDraft(val toEmail: String, val subject: String, val body: String)

    @Volatile
    private var draft: PendingDraft? = null
    @Volatile
    private var lastSummary: String = ""

    fun draftPending(): Boolean = draft != null

    fun notConnectedHint(): String = "Gmail ekhono connect kora hoy nai, Sir. Settings Control Center theke Gmail Auto Reply khule apnar address ar 16 character App Password save korun."

    suspend fun readLatest(context: Context): String {
        if (!GmailStore.isConnected(context)) return notConnectedHint()
        return try {
            val list = GmailClient.fetchLatest(context, 3)
            if (list.isEmpty()) {
                "Apnar inbox ekdom faka, Sir."
            } else {
                val newest = list.first()
                val builder = StringBuilder("Apnar latest email, Sir. " + newest.fromName + " theke, subject: " + newest.subject + ". ")
                builder.append(if (newest.body.isBlank()) "" else "Bol: " + newest.body)
                if (list.size > 1) builder.append(" Aro " + (list.size - 1) + " ta notun email ache.")
                builder.toString()
            }
        } catch (e: Exception) {
            "Gmail theke email ane parini, Sir. " + (e.message ?: "")
        }
    }

    suspend fun summarize(context: Context): String {
        if (!GmailStore.isConnected(context)) return notConnectedHint()
        return try {
            val list = GmailClient.fetchLatest(context, 5)
            if (list.isEmpty()) {
                lastSummary = ""
                "Apnar inbox e kono email nei, Sir."
            } else {
                val builder = StringBuilder("Apnar inbox e " + list.size + " ta email, Sir. ")
                for ((index, m) in list.withIndex()) {
                    builder.append((index + 1).toString() + ". " + m.fromName + " - " + m.subject + ". ")
                }
                lastSummary = list.joinToString(", ") { it.fromEmail }
                builder.append("Details shunate bolun taile shesh email ta porhi.")
                builder.toString()
            }
        } catch (e: Exception) {
            "Gmail summary banate parini, Sir. " + (e.message ?: "")
        }
    }

    suspend fun draftReply(context: Context, instruction: String): String {
        if (!GmailStore.isConnected(context)) return notConnectedHint()
        return try {
            val list = GmailClient.fetchLatest(context, 1)
            if (list.isEmpty()) return "Reply korar moto kono email nei, Sir."
            val newest = list.first()
            if (newest.fromEmail.equals(GmailStore.email(context), ignoreCase = true)) {
                return "Apnar shesh email apnar nijer address theke eseche, Sir - er reply pathanor dorkar nei."
            }
            val template = GmailStore.template(context)
            val body = template.replace("{name}", newest.fromName)
            draft = PendingDraft(toEmail = newest.fromEmail, subject = "Re: " + newest.subject, body = body)
            "Reply draft ready kore diyechi, Sir. " + newest.fromName + " ke, subject: Re: " + newest.subject + ". Pathanor age bolo - send it, na thik ache bolle pathiye dibo."
        } catch (e: Exception) {
            "Draft banate parini, Sir. " + (e.message ?: "")
        }
    }

    suspend fun sendDraft(context: Context): String {
        val pending = draft ?: return "Age ekta reply draft korun, Sir - bolo: draft a reply."
        return try {
            GmailClient.send(context, pending.toEmail, pending.subject, pending.body)
            draft = null
            "Email pathiye diyechi, Sir."
        } catch (e: Exception) {
            "Email pathate parini, Sir. " + (e.message ?: "")
        }
    }

    /** Called from the shared ConfirmAction flow: returns null when no Gmail draft is pending. */
    suspend fun onConfirm(context: Context, confirmed: Boolean): String? {
        if (draft == null) return null
        return if (confirmed) {
            val result = sendDraft(context)
            "Confirm korechen - " + result
        } else {
            draft = null
            "Theek ache Sir, reply draft cancel kore diyechi."
        }
    }
}