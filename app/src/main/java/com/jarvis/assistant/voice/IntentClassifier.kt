package com.jarvis.assistant.voice

import java.util.Locale

/** The eight utterance kinds the conversation layer has to keep apart. */
enum class IntentCategory {
    DEVICE_COMMAND,
    CONVERSATIONAL_QUERY,
    FOLLOW_UP,
    CORRECTION,
    BACKGROUND_SPEECH,
    AMBIENT_NOISE,
    MULTI_ACTION_COMMAND,
    UNCERTAIN_INTENT
}

data class IntentClassification(
    val category: IntentCategory,
    val confidence: Float,
    val actionCount: Int = 0,
    val reason: String = ""
)

/**
 * Local, rule-based utterance understanding. Runs in microseconds, on the phone, with no
 * network call: this is what stops a simple "open YouTube" from being shipped to a large
 * model, and what stops a television in the next room from being treated as a command.
 *
 * It is deliberately honest about uncertainty: a risky verb with no target ("call", "send")
 * is never promoted to a command, and anything the rules cannot place is reported as
 * UNCERTAIN_INTENT so the caller can ask instead of guessing.
 */
object IntentClassifier {

    private val WAKE_PREFIXES = listOf("hey jarvis", "ok jarvis", "okay jarvis", "jarvis")

    private val FILLERS = setOf("uh", "um", "hmm", "mmm", "ah", "oh", "eh", "mm", "hm", "ahh", "er", "uhh")

    private val SINGLE_VERBS = setOf(
        "open", "launch", "start", "run", "play", "pause", "resume", "stop", "call", "dial",
        "text", "message", "send", "set", "turn", "enable", "disable", "switch", "toggle",
        "increase", "decrease", "raise", "lower", "mute", "unmute", "close", "kill", "restart",
        "reboot", "lock", "unlock", "scan", "translate", "calculate", "navigate", "remind",
        "alarm", "screenshot", "install", "uninstall", "delete", "ring", "vibrate"
    )

    private val PHRASE_VERBS = listOf(
        "whatsapp ", "take a photo", "take a picture", "go to ", "search for ", "read out ",
        "show me ", "how much", "how many", "screen shot", "flash light", "flashlight on",
        "flashlight off", "wifi on", "wifi off", "bluetooth on", "bluetooth off"
    )

    /** Verbs that change the world in a way the user would want confirmed when unclear. */
    private val RISKY_VERBS = setOf(
        "call", "dial", "text", "message", "send", "delete", "uninstall", "pay", "order",
        "transfer", "ring", "restart", "reboot"
    )

    private val CONVERSATIONAL = listOf(
        "why ", "how ", "what ", "who ", "when ", "where ", "which ", "explain", "describe",
        "compare", "summar", "difference between", "do you think", "can you explain",
        "tell me about", "what's", "whats", "is it", "are you", "should i", "would you"
    )

    private val FOLLOW_UP = listOf(
        "and ", "also ", "then ", "what about", "how about", "more", "another", "same",
        "continue", "go on", "one more", "next", "keep going", "why not"
    )

    private val CORRECTION_STRICT = listOf(
        "i meant", "i said", "instead", "rather", "actually", "not that", "wrong one",
        "that's wrong", "that is wrong", "cancel that", "scratch that", "change that",
        "not this", "no, ", "no,", "no."
    )

    /** Wording that only ever comes out of a television or a podcast, never out of a request. */
    private val BACKGROUND_MARKERS = listOf(
        "breaking news", "in other news", "next up", "stay tuned", "welcome back",
        "coming up next", "thanks for watching", "subscribe to", "in today's episode",
        "sponsored by", "yesterday's game", "the weather tomorrow will"
    )

    fun classify(rawText: String): IntentClassification {
        val text = normalise(rawText)
        if (text.isEmpty()) return IntentClassification(IntentCategory.AMBIENT_NOISE, 0.90f, 0, "empty transcript")

        val words = text.split(" ").filter { it.isNotEmpty() }
        if (words.size == 1 && words[0].length <= 2) {
            return IntentClassification(IntentCategory.AMBIENT_NOISE, 0.85f, 0, "utterance too short to be a request")
        }
        if (words.all { it in FILLERS }) {
            return IntentClassification(IntentCategory.AMBIENT_NOISE, 0.85f, 0, "only filler sounds")
        }

        val correction = CORRECTION_STRICT.firstOrNull { marker ->
            text.contains(marker) || text == marker.trim()
        } ?: if (text.startsWith("no ") && clauseHasVerb(text)) "no" else null
        if (correction != null) {
            return IntentClassification(
                IntentCategory.CORRECTION, 0.88f, actionCount(text),
                "correction marker \"" + correction.trim() + "\" - replace the previous action"
            )
        }

        val actions = actionCount(text)
        if (actions >= 2) {
            return IntentClassification(
                IntentCategory.MULTI_ACTION_COMMAND, 0.85f, actions,
                "$actions device actions in one utterance"
            )
        }

        if (actions == 1) {
            val verb = firstVerb(text)
            val target = targetAfterVerb(text)
            if (verb != null && verb in RISKY_VERBS && target.isEmpty()) {
                return IntentClassification(
                    IntentCategory.UNCERTAIN_INTENT, 0.45f, 1,
                    "risky verb \"$verb\" without a target - confirm before acting"
                )
            }
            return IntentClassification(
                IntentCategory.DEVICE_COMMAND, if (target.isEmpty()) 0.70f else 0.92f, 1,
                "local command" + if (verb != null) " (" + verb + ")" else ""
            )
        }

        if (BACKGROUND_MARKERS.any { text.contains(it) }) {
            return IntentClassification(
                IntentCategory.BACKGROUND_SPEECH, 0.78f, 0,
                "wording typical of television or podcast narration"
            )
        }

        if (FOLLOW_UP.any { text == it.trim() || text.startsWith(it) }) {
            return IntentClassification(IntentCategory.FOLLOW_UP, 0.72f, 0, "continues the previous turn")
        }

        if (text.endsWith("?") || CONVERSATIONAL.any { text.startsWith(it) || text == it.trim() }) {
            return IntentClassification(IntentCategory.CONVERSATIONAL_QUERY, 0.82f, 0, "question or explanation request")
        }

        if (words.size >= 3) {
            return IntentClassification(IntentCategory.UNCERTAIN_INTENT, 0.45f, 0, "no device verb and not a clear question")
        }

        return IntentClassification(IntentCategory.UNCERTAIN_INTENT, 0.40f, 0, "not enough signal to decide")
    }

    fun normalise(rawText: String): String {
        var text = rawText.lowercase(Locale.ROOT).trim()
        for (prefix in WAKE_PREFIXES) {
            if (text.startsWith(prefix)) {
                val rest = text.substring(prefix.length).trim()
                if (rest.isEmpty() || rest[0] == ',' || rest[0] == '.') {
                    text = rest.trimStart(',', '.', ' ')
                }
            }
        }
        text = text.replace(Regex("[\\u0000-\\u001f]"), " ")
        text = text.replace(Regex("\\s+"), " ").trim()
        return text
    }

    fun actionCount(text: String): Int {
        val clauses = text
            .split(Regex("\\b(?:and then|then|and also|and|, then|plus)\\b"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (clauses.isEmpty()) return 0
        return clauses.count { clauseHasVerb(it) }
    }

    fun clauseHasVerb(clause: String): Boolean {
        val padded = " " + clause + " "
        if (SINGLE_VERBS.any { padded.contains(" " + it + " ") }) return true
        return PHRASE_VERBS.any { clause.contains(it) }
    }

    /** The risky verb in an utterance ("send", "call", "delete"), if any - used to ask before acting. */
    fun riskVerb(text: String): String? {
        val padded = " " + normalise(text) + " "
        return RISKY_VERBS.firstOrNull { padded.contains(" " + it + " ") }
    }

    private fun firstVerb(text: String): String? {
        val padded = " " + text + " "
        return SINGLE_VERBS.firstOrNull { padded.contains(" " + it + " ") }
    }

    private fun targetAfterVerb(text: String): String {
        val verb = firstVerb(text) ?: return ""
        val idx = text.indexOf(verb)
        if (idx < 0) return ""
        val rest = text.substring(idx + verb.length).trim()
            .replace(Regex("^(?:the|a|an|my|on|off|to|for|up|down)\\s+"), "")
            .trim()
        return rest
    }
}
