package com.jarvis.assistant.automation.command

/**
 * DEV STYLE: conversation awareness — when the operator is talking to someone
 * else nearby, overheard speech must NOT trigger device commands. A spoken line
 * counts as addressed to JARVIS when it uses the wake word, when it is part of
 * an ongoing exchange (JARVIS spoke or acted moments ago), or when it is an
 * unambiguous high-confidence device command.
 */
object SpeechAddressFilter {

    private val wakeWord = Regex("""(?i)\bjarvis\b|জার্ভিস""")

    fun isLikelyAddressedToAssistant(
        utterance: String,
        recentExchange: Boolean,
        knownCommand: Boolean
    ): Boolean {
        val text = utterance.trim()
        if (text.isEmpty()) return false
        if (wakeWord.containsMatchIn(text)) return true
        if (recentExchange) return true
        return knownCommand
    }
}
