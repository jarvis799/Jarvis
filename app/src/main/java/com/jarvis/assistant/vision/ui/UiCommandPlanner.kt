package com.jarvis.assistant.vision.ui

import java.util.Locale

/**
 * Turns a spoken UI command such as "tap the Search button" into a structured plan
 * (target + intended action + expected result). Anything that is not a UI command
 * returns null so the existing device/app command routing stays exactly as it was.
 *
 * Understands English, Banglish and Bangla phrasing in both verb-first
 * ("tap the search button") and verb-last ("search button e tap koro") order.
 */
object UiCommandPlanner {

    private val wakePrefix = Regex("^(hey |ok |okay |o )?jarvis[, ]*", RegexOption.IGNORE_CASE)
    private val punctuation = Regex("[\\p{Punct}]+")
    private val spaces = Regex("\\s+")

    private val describePhrases = listOf(
        "what's on the screen", "whats on the screen", "what's on screen", "what is on the screen",
        "what is on screen", "describe the screen", "explain the screen", "read the screen",
        "read out the screen", "tell me what's on the screen", "screen e ki ache", "screen e ki ase",
        "screen e ki", "porday ki ache", "screen er ki", "screen ta bolo", "screen poro", "screen describe koro"
    )

    private val clickVerbs = listOf(
        "tap", "click", "press", "select", "choose", "hit", "chap",
        "tap koro", "tap kore", "click koro", "click kore", "press koro", "chap dao", "tap dao", "select koro", "choose koro"
    )
    private val typeVerbs = listOf(
        "type", "enter", "write", "input", "likho", "lekho", "likhe dao", "type koro", "enter koro", "write koro"
    )
    private val scrollDownWords = listOf(
        "scroll down", "scroll niche", "niche scroll", "scroll down koro", "slide down", "page down",
        "swipe up", "scroll koro niche", "niche jao scroll", "scroll e niche"
    )
    private val scrollUpWords = listOf(
        "scroll up", "scroll upore", "upore scroll", "scroll up koro", "slide up", "page up",
        "swipe down", "scroll koro upore", "upore jao scroll", "scroll e upore"
    )
    private val seekWords = listOf("find", "look for", "search for", "locate", "khuje", "khujo", "khoj")
    private val backPhrases = listOf("back", "go back", "back please", "back koro", "pichone jao", "ferot jao", "back e jao", "pichone cholo")
    private val homePhrases = listOf("go home", "home e jao", "home screen e jao", "home e cholo", "go to home screen")
    private val recentsPhrases = listOf("recent apps", "recent app", "recent apps kholo", "app switcher", "recents kholo")

    private val toggleWords = listOf("check", "uncheck", "toggle", "turn on", "turn off", "switch on", "switch off", "tick", "on koro", "off koro", "untick")
    private val sensitiveWords = listOf(
        "delete", "remove", "uninstall", "buy", "purchase", "pay", "payment", "order", "subscribe",
        "send", "post", "publish", "share", "confirm", "log out", "logout", "sign out", "factory reset",
        "format", "block", "report", "transfer", "withdraw", "unfollow", "unfriend", "leave group"
    )
    private val passwordWords = listOf("password", "passcode", "pin", "cvv", "card number", "otp", "upi", "netbanking", "secret")

    private val labelNoise = setOf(
        "the", "a", "an", "button", "tab", "icon", "option", "item", "field", "link", "menu",
        "checkbox", "toggle", "switch", "please", "now", "for", "me", "on", "at", "in", "to", "of", "my",
        "e", "te", "ta", "er", "ke", "tay", "ekhane", "okhane"
    )

    fun plan(utterance: String): UiCommandPlan? {
        val raw = wakePrefix.replace(utterance.trim(), "").trim()
        if (raw.isBlank()) return null
        val lower = raw.lowercase(Locale.ROOT)

        if (describePhrases.any { lower.contains(it) }) {
            return UiCommandPlan(UiActionKind.DESCRIBE, expected = "screen contents described", originalUtterance = raw)
        }
        if (matchesPhrase(lower, backPhrases)) {
            return UiCommandPlan(UiActionKind.BACK, expected = "previous screen", originalUtterance = raw)
        }
        if (matchesPhrase(lower, homePhrases)) {
            return UiCommandPlan(UiActionKind.HOME, expected = "home screen", originalUtterance = raw)
        }
        if (matchesPhrase(lower, recentsPhrases)) {
            return UiCommandPlan(UiActionKind.RECENTS, expected = "recent apps", originalUtterance = raw)
        }
        return planTyping(raw, lower) ?: planScrolling(raw, lower) ?: planClicking(raw, lower)
    }

    /** Whole-word verb match, so "selected" is not read as the verb "select". */
    private fun hasWord(text: String, word: String): Boolean {
        return Regex("(^|\\s)" + Regex.escape(word) + "(\\s|$)").containsMatchIn(text)
    }

    private fun matchesPhrase(lower: String, phrases: List<String>): Boolean {
        val clean = lower.trim().trimEnd('.', '!', '?', ',')
        return phrases.any { clean == it || clean == it + " please" }
    }

    // ---------------------------------------------------------------- typing

    private val quoted = Regex("[\"\u201c\u201d']([^\"\u201c\u201d']{1,200})[\"\u201c\u201d']")

    private fun planTyping(raw: String, lower: String): UiCommandPlan? {
        // "press enter" / "tap enter" is a click on the Enter key, never a text input.
        if (lower.contains("press enter") || lower.contains("tap enter") || lower.contains("enter key")) return null
        val verb = typeVerbs.firstOrNull { hasWord(lower, it) } ?: return null
        val quotedText = quoted.find(raw)?.groupValues?.get(1)?.trim()
        val payload = quotedText ?: afterVerb(raw, verb)?.let { cutAtTargetClause(it) }?.trim().orEmpty()
        // Blank or deictic dictation ("enter this text") has no literal payload here, so it stays
        // with the existing smart path instead of typing the words "this text".
        if (payload.isBlank() || isDeictic(payload)) return null
        val target = fieldTarget(lower)
        val sensitive = passwordWords.any { lower.contains(it) }
        return UiCommandPlan(
            action = UiActionKind.SET_TEXT,
            target = target,
            text = payload,
            expected = "text " + payload + " typed into " + (target?.query?.ifBlank { "the selected field" } ?: "the selected field"),
            needsConfirmation = sensitive,
            confirmationQuestion = if (sensitive) "That looks like a password or payment field. Should I really type into it?" else null,
            originalUtterance = raw
        )
    }

    private fun fieldTarget(lower: String): UiTargetSpec {
        val named = Regex("(?:in|into|inside|to)\\s+the\\s+([a-z0-9 ]{2,30}?)\\s+(?:field|box|bar|input)").find(lower)?.groupValues?.get(1)?.trim()
        val namedBangla = Regex("([a-z0-9 ]{2,30}?)\\s+(?:field|box|bar) e\\s").find(lower)?.groupValues?.get(1)?.trim()
        val query = named ?: namedBangla
        return if (!query.isNullOrBlank()) {
            UiTargetSpec(query = query, prefersEditable = true, hints = listOf("editable"))
        } else {
            UiTargetSpec(query = "", source = UiTargetSource.SELECTED_FIELD, prefersEditable = true, hints = listOf("editable"))
        }
    }

    // -------------------------------------------------------------- scrolling

    private fun planScrolling(raw: String, lower: String): UiCommandPlan? {
        val down = scrollDownWords.any { hasWord(lower, it) }
        val up = scrollUpWords.any { hasWord(lower, it) }
        if (!down && !up) return null
        val sought = seekQuery(lower)
        val ordinal = ordinalOf(lower)
        val attempts = 4
        return if (sought != null) {
            val query = stripOrdinalWord(sought)
            UiCommandPlan(
                action = UiActionKind.FIND_AND_ACT,
                target = UiTargetSpec(
                    query = query,
                    ordinal = if (query.isBlank()) ordinal else null,
                    hints = listOf("seek", if (down) "scroll_forward" else "scroll_backward")
                ),
                expected = "found " + (if (query.isBlank()) "the requested option" else query) + " after scrolling",
                maxAttempts = attempts,
                needsConfirmation = sensitiveWords.any { lower.contains(it) },
                confirmationQuestion = "That may change something important. Should I continue?",
                originalUtterance = raw
            )
        } else {
            UiCommandPlan(
                action = if (down) UiActionKind.SCROLL_FORWARD else UiActionKind.SCROLL_BACKWARD,
                expected = if (down) "screen scrolled forward" else "screen scrolled back",
                maxAttempts = 3,
                originalUtterance = raw
            )
        }
    }

    private fun seekQuery(lower: String): String? {
        val seek = seekWords.firstOrNull { lower.contains(it) } ?: return null
        val tail = afterVerb(lower, seek) ?: return null
        val query = cleanLabel(cutAtActionClause(tail))
        return query.ifBlank { null }
    }

    // --------------------------------------------------------------- clicking

    private val deictic = setOf(
        "this", "this text", "this sentence", "that", "that text", "it", "this one",
        "eta", "ei text", "ei lekha", "the text", "the following"
    )

    private fun isDeictic(payload: String): Boolean = payload.lowercase(Locale.ROOT).trim() in deictic

    private fun planClicking(raw: String, lower: String): UiCommandPlan? {
        val seekOnly = seekWords.firstOrNull { lower.startsWith(it) || lower.contains(it + " the ") }
        val verb = clickVerbs.firstOrNull { hasWord(lower, it) }
        if (verb == null && seekOnly == null) return null
        val phrase = seekOnly?.let { afterVerb(raw, it) } ?: afterVerb(raw, verb ?: return null) ?: return null
        val cleanedPhrase = cutAtActionClause(phrase)
        val ordinal = ordinalOf(lower)
        val query = stripOrdinalWord(cleanLabel(cleanedPhrase))
        if (query.isBlank() && ordinal == null) return null
        val sensitive = sensitiveWords.any { lower.contains(it) }
        val toggle = toggleWords.any { lower.contains(it) }
        val source = if (lower.contains(" id ") || lower.contains("with id ")) UiTargetSource.RESOURCE_ID else UiTargetSource.TEXT
        val hints = buildList {
            if (seekOnly != null) add("seek")
            if (lower.contains("button")) add("button")
            if (toggle) add("toggle")
        }
        return UiCommandPlan(
            action = UiActionKind.CLICK,
            target = UiTargetSpec(query = query, source = source, ordinal = ordinal, isToggle = toggle, hints = hints),
            expected = "target " + (query.ifBlank { "option " + (ordinal ?: 1) }) + " opened",
            needsConfirmation = sensitive,
            confirmationQuestion = if (sensitive) "That looks like it changes something important. Should I continue?" else null,
            originalUtterance = raw
        )
    }

    private val ordinalWords = setOf(
        "first", "one", "ek", "prothom", "second", "two", "dui", "ditio",
        "third", "three", "tin", "tritio", "next", "porer", "last", "shesh"
    )

    /** "the next option" is a position, not a text to search for. */
    private fun stripOrdinalWord(query: String): String {
        return query.split(" ").filter { it.isNotBlank() && it !in ordinalWords }.joinToString(" ")
    }

    private fun ordinalOf(lower: String): Int? {
        val words = mapOf(
            "first" to 1, "1st" to 1, "number one" to 1, "one" to 1, "prothom" to 1, "ek" to 1,
            "second" to 2, "2nd" to 2, "number two" to 2, "two" to 2, "ditio" to 2, "dui" to 2,
            "third" to 3, "3rd" to 3, "number three" to 3, "three" to 3, "tritio" to 3, "tin" to 3,
            "next" to 1, "porer" to 1, "last" to -1, "shesh" to -1
        )
        for ((word, value) in words) {
            if (Regex("\\b" + Regex.escape(word) + "\\b").containsMatchIn(lower)) return value
        }
        return null
    }

    // ---------------------------------------------------------------- helpers

    private fun afterVerb(text: String, verb: String): String? {
        val idx = text.lowercase(Locale.ROOT).indexOf(verb)
        if (idx < 0) return null
        val before = text.substring(0, idx).trim()
        val after = text.substring(idx + verb.length).trim()
        // Verb-last phrasing ("search button e tap koro"): the target sits before the verb.
        return if (after.isEmpty() || isFillerTail(after)) before.ifBlank { null } else after
    }

    private fun isFillerTail(tail: String): Boolean {
        val t = tail.lowercase(Locale.ROOT).trim().trimEnd('.', '!', '?', ',')
        return t in setOf("koro", "kore", "kor", "dao", "do", "please", "plz", "now", "ta", "e", "te", "a", "ektu", "and open it", "and tap it", "and click it", "koro ekhon")
    }

    private fun cutAtTargetClause(text: String): String {
        var out = text
        val cuts = listOf(" in the ", " into the ", " inside the ", " in this ", " in that ", " field e ", " e likho", " te likho", " to the ")
        for (c in cuts) {
            val i = out.lowercase(Locale.ROOT).indexOf(c)
            if (i > 0) out = out.substring(0, i)
        }
        return out.trim()
    }

    private fun cutAtActionClause(text: String): String {
        var out = cutAtTargetClause(text)
        val cuts = listOf(" then ", " and press", " and tap", " and click", " and open", " and select", " koro", " kore", " please", " for me", " ekhon", " now", " ar ")
        for (c in cuts) {
            val i = out.lowercase(Locale.ROOT).indexOf(c)
            if (i > 0) out = out.substring(0, i)
        }
        return out.trim()
    }

    private fun cleanLabel(text: String): String {
        val noPunct = punctuation.replace(text, " ").replace(spaces, " ").trim()
        val words = noPunct.split(" ").filter { it.isNotBlank() && it.lowercase(Locale.ROOT) !in labelNoise }
        return words.joinToString(" ").lowercase(Locale.ROOT).trim()
    }
}
