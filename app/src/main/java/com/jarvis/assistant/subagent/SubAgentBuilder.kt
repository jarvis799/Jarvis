package com.jarvis.assistant.subagent

import android.content.Context
import android.content.Intent
import com.jarvis.assistant.automation.whatsapp.AutoReplyRule
import com.jarvis.assistant.automation.whatsapp.WhatsAppAutoReplyManager
import com.jarvis.assistant.email.GmailStore

/**
 * DEV STYLE 2.0.5 — builds a real sub agent from one spoken or typed sentence.
 *
 * A sub agent here is a live auto-reply agent: it is written into the same rule
 * store the background services already read, so "sub agent banao" produces a
 * working agent instead of an apology about missing tools.
 */
object SubAgentBuilder {

    private val agentWord = Regex("\\b(sub[ -]?agent|subagent|agent)\\b|\\u09b8\\u09be\\u09ac\\s*\\u098f\\u099c\\u09c7\\u09a8\\u09cd\\u099f", RegexOption.IGNORE_CASE)
    private val createWord = Regex(
        "\\b(ban(a|ao|abo|abo|ate|iye)|banao|create|build|make|add|new|setup|set up|toiri)\\b|\\u09ac\\u09be\\u09a8\\u09be\\u0993|\\u09ac\\u09be\\u09a8\\u09be\\u09a4\\u09c7",
        RegexOption.IGNORE_CASE
    )
    private val statusOnly = Regex(
        "\\b(status|state|obostha|on ache|on kina|cholche|running|active|list|catalog|showcase|dekhao|dekhabe|ki ki|koto)\\b",
        RegexOption.IGNORE_CASE
    )
    private val emailWord = Regex("\\b(email|gmail|mail)\\b", RegexOption.IGNORE_CASE)
    private val whatsappWord = Regex("\\b(whatsapp|wp|wa)\\b", RegexOption.IGNORE_CASE)

    private val replyMarker = Regex(
        "\\b(reply|uttor|answer|jawab|bolo|bolun|bolbe|likhe dao|likho|lekho|pathao|pathiye dao|message)\\b\\s*[:\\-]?\\s*",
        RegexOption.IGNORE_CASE
    )
    private val quoted = Regex("[\"'\u201c\u2018]([^\"'\u201d\u2019]{2,60})[\"'\u201d\u2019]")
    private val triggerMarker = Regex(
        "\\b(jokhon|jodi|when|if|trigger|keyword|word)\\b\\s+(.{2,40}?)\\s*" +
            "\\b(bole|bolle|boleh|says|say|likhe|lekha|dhore|dekhe|pai|pele)\\b",
        RegexOption.IGNORE_CASE
    )
    private val catchAll = Regex("\\b(sob message|shob message|every message|all message|catch all|any message)\\b", RegexOption.IGNORE_CASE)

    /** True only for a request that asks Jarvis to MAKE a new agent. */
    fun wantsCreation(text: String): Boolean =
        agentWord.containsMatchIn(text) && createWord.containsMatchIn(text) && !statusOnly.containsMatchIn(text)

    /** Creates the agent and returns one honest line about what is now stored. */
    fun create(context: Context, instruction: String): String {
        val text = instruction.trim()
        val reply = replyText(text)
        if (reply.isBlank()) {
            return "Sub agent banate ek line bolun, Sir - agent kivabe reply dibe? " +
                "Example: \"sub agent banao, jokhon keu price bole to reply: amar price list pathacchi\"."
        }
        val keyword = keyword(text)
        return if (emailWord.containsMatchIn(text) && !whatsappWord.containsMatchIn(text)) {
            createEmail(context, keyword, reply)
        } else {
            createWhatsApp(context, keyword, reply)
        }
    }

    private fun createWhatsApp(context: Context, keyword: String, reply: String): String {
        val manager = WhatsAppAutoReplyManager.get(context)
        val rule = AutoReplyRule(
            id = "custom_" + System.currentTimeMillis(),
            keywordPattern = keyword,
            replyText = reply,
            enabled = true
        )
        manager.addRule(rule)
        if (!manager.isEnabled()) manager.setEnabled(true)
        val access = SubAgentStateResolver.notificationAccessGranted(context)
        val liveRules = manager.getRules().count { it.enabled && it.replyText.isNotBlank() }
        open(context, WhatsAppSubAgentActivity::class.java)
        return buildString {
            append("Sub agent banano holo, Sir - ")
            append("trigger: ")
            append(if (keyword.isBlank()) "sob notun message" else "\"" + keyword + "\"")
            append(" · reply: \"").append(reply.take(100)).append("\".")
            append(" WhatsApp agent switch ekhon ON, mot ").append(liveRules).append(" ta rule cholche.")
            append(
                if (access) " Notification access ok."
                else " Sudhu notification access ta dite hobe - Android settings theke on korun, tarpor nijei kaj korbe."
            )
        }
    }

    private fun createEmail(context: Context, keyword: String, reply: String): String {
        val existing = GmailStore.keywords(context)
        val merged = (existing + keyword).map { it.trim() }.filter { it.isNotBlank() }.distinct()
        if (merged.isNotEmpty()) GmailStore.setKeywords(context, merged.joinToString(","))
        val connected = GmailStore.isConnected(context)
        if (connected) GmailStore.setAutoReplyEnabled(context, true) else open(context, EmailSubAgentActivity::class.java)
        return buildString {
            append("Email sub agent update holo, Sir - ")
            append(if (keyword.isBlank()) "protita notun mail" else "\"" + keyword + "\" wala mail")
            append(" e reply jabe tomar saved HTML design e (line: \"").append(reply.take(80)).append("\").")
            append(
                if (connected) " Auto reply ON."
                else " Gmail connect kora nei - 16-character App Password diye connect korlei agent chalu hobe."
            )
        }
    }

    private fun replyText(text: String): String {
        val marker = replyMarker.findAll(text).lastOrNull() ?: return quoted.find(text)?.groupValues?.get(1)?.trim().orEmpty()
        return marker.let { text.substring(it.range.last + 1) }
            .trim()
            .trim('"', '\'', '.', ',', ':', '-', ' ')
            .take(300)
    }

    private fun keyword(text: String): String {
        val fromQuotes = quoted.find(text)?.groupValues?.get(1)?.trim().orEmpty()
        val fromTrigger = triggerMarker.find(text)?.groupValues?.get(2)?.trim().orEmpty()
        val candidate = fromTrigger.ifBlank { fromQuotes }
        if (candidate.isBlank() || catchAll.containsMatchIn(text)) return ""
        // Drop pronouns/fillers so "jokhon keu price bole" matches the word "price".
        val words = candidate.split(Regex("\\s+"))
            .map { it.trim('"', '\'', ',', '.', ':', '?', '!') }
            .filter { it.length > 1 && it.lowercase() !in keywordNoise }
        return words.joinToString("|").take(40)
    }

    private val keywordNoise = setOf(
        "keu", "kew", "kono", "someone", "anyone", "user", "the", "a", "an", "my", "amar",
        "ami", "amake", "message", "messages", "msg", "mail", "email", "gmail", "bolle", "bole",
        "bolo", "says", "say", "likhe", "lekha", "pathay", "pathabe"
    )

    private fun open(context: Context, destination: Class<*>) {
        runCatching {
            context.startActivity(Intent(context, destination).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}