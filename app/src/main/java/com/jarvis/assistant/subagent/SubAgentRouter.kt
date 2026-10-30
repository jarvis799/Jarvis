package com.jarvis.assistant.subagent

import android.content.Context
import android.content.Intent
import com.jarvis.assistant.automation.whatsapp.WhatsAppAutoReplyManager
import com.jarvis.assistant.email.GmailStore

/**
 * DEV STYLE 2.0.1 — Sub Agent routing for the conversation layer.
 *
 * Jarvis answers about a sub agent with its REAL state and opens only the
 * screen that owns it. It never claims an agent has been switched on: the
 * owner flips that switch on the screen itself.
 */
object SubAgentRouter {

    private val statusWords = Regex("\\b(status|state|obostha|on ache|on kina|cholche|running|active|setup)\\b", RegexOption.IGNORE_CASE)

    fun route(context: Context, request: String): String? {
        val text = request.lowercase()
        val mentionsWhatsAppAgent = text.contains("whatsapp") && Regex("auto[ -]?reply|auto reply|agent").containsMatchIn(text)
        val mentionsEmailAgent = (text.contains("email") || text.contains("gmail")) &&
            Regex("auto[ -]?reply|agent|html template|reply template").containsMatchIn(text)
        val mentionsShowcase = Regex("sub[ -]?agent|agent showcase|agent list").containsMatchIn(text)

        if (!mentionsWhatsAppAgent && !mentionsEmailAgent && !mentionsShowcase) return null

        val wantsStatus = statusWords.containsMatchIn(text)

        return when {
            mentionsWhatsAppAgent -> {
                if (wantsStatus) whatsappStatus(context)
                else open(context, WhatsAppSubAgentActivity::class.java, "WhatsApp sub agent kholche, Sir.")
            }
            mentionsEmailAgent -> {
                if (wantsStatus) emailStatus(context)
                else open(context, EmailSubAgentActivity::class.java, "Email sub agent kholche, Sir. HTML reply design oikhane.")
            }
            else -> open(context, SubAgentActivity::class.java, "Sub Agent showcase kholche, Sir.")
        }
    }

    private fun whatsappStatus(context: Context): String {
        val manager = WhatsAppAutoReplyManager.get(context)
        val rules = manager.getRules().count { it.enabled && it.replyText.isNotBlank() }
        val access = if (SubAgentStateResolver.notificationAccessGranted(context)) "ache" else "nei"
        return buildString {
            append("WhatsApp sub agent: ")
            append(if (manager.isEnabled()) "ON" else "OFF")
            append(" · ")
            append(rules)
            append(" reply rule · notification access ")
            append(access)
            append(".")
            if (rules == 0) append(" Age ekta reply rule banate hobe.")
            if (access == "nei") append(" Android settings theke notification access dite hobe.")
        }
    }

    private fun emailStatus(context: Context): String {
        val connected = GmailStore.isConnected(context)
        val enabled = GmailStore.autoReplyEnabled(context)
        return buildString {
            append("Email sub agent: ")
            append(if (enabled) "ON" else "OFF")
            append(" · Gmail ")
            append(if (connected) "connected (" + GmailStore.email(context) + ")" else "connect kora nei")
            append(" · HTML design ")
            append(if (GmailStore.htmlReplyEnabled(context)) "on" else "off")
            append(".")
            if (!connected) append(" 16-character App Password diye connect korte hobe.")
        }
    }

    private fun open(context: Context, destination: Class<*>, message: String): String {
        runCatching {
            context.startActivity(
                Intent(context, destination).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
        return message
    }
}