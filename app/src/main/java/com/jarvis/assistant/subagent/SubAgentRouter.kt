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

    /** DEV STYLE 2.0.2: "ki kore / kivabe kaj kore" questions get a real explanation, not a screen. */
    private val explainWords = Regex(
        "\\b(ki kore|kivabe|kemon kore|how (does|do|to)|what (does|do|is)|explain|ki kaj|ki korte|kaaj|works?)\\b",
        RegexOption.IGNORE_CASE
    )

    fun route(context: Context, request: String): String? {
        // DEV STYLE 2.0.5: "sub agent banao" builds and stores a real working agent.
        if (SubAgentBuilder.wantsCreation(request)) return SubAgentBuilder.create(context, request)
        val text = request.lowercase()
        val mentionsWhatsAppAgent = text.contains("whatsapp") && Regex("auto[ -]?reply|auto reply|agent").containsMatchIn(text)
        val mentionsEmailAgent = (text.contains("email") || text.contains("gmail")) &&
            Regex("auto[ -]?reply|agent|html template|reply template").containsMatchIn(text)
        val mentionsShowcase = Regex("sub[ -]?agent|agent showcase|agent list").containsMatchIn(text)

        if (!mentionsWhatsAppAgent && !mentionsEmailAgent && !mentionsShowcase) return null

        val wantsStatus = statusWords.containsMatchIn(text)

        // Answer "what / how does it work" in words; only open a screen for a direct request.
        if (!wantsStatus && explainWords.containsMatchIn(text)) {
            return explanation(mentionsWhatsAppAgent, mentionsEmailAgent)
        }

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

    /** What each sub agent does, plus the one step that turns it on. */
    private fun explanation(whatsapp: Boolean, email: Boolean): String = when {
        whatsapp ->
            "WhatsApp sub agent: notun message notification theke dekhe tomar rule onujayi nijei reply dey. " +
                "On korte: Sub Agent > WhatsApp Agent, notification access dao ar ekta reply rule enable koro."
        email ->
            "Email sub agent: Gmail e 16-character App Password diye connect kore, unread mail pore tomar HTML " +
                "design e reply dey. On korte: Sub Agent > Email Agent, Gmail connect koro ar Auto reply on koro."
        else ->
            "Sub agent mane Jarvis er background helper - tumi switch on korle se nijei kaj kore. Duita ache: " +
                "WhatsApp Agent (notification theke auto reply) ar Email Agent (Gmail auto reply). " +
                "Python lage na - sub agent gulo Kotlin e chole; terminal er Python sudhu shell command er jonno."
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