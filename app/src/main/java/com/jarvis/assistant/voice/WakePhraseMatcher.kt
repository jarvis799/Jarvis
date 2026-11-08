package com.jarvis.assistant.voice

/**
 * Wake-word decision rules.
 *
 * The previous check required the recognised utterance to be exactly one of the
 * configured phrases, so a real wake-up such as "hey jarvis are you there" or a
 * phrase split across two results was always rejected. Any utterance that
 * contains the trigger word now wakes Jarvis, and confidence is judged on the
 * trigger word itself instead of the whole sentence.
 */
object WakePhraseMatcher {

    private val FILLERS = setOf(
        "hey", "hi", "hello", "ok", "okay", "wake", "up", "sun", "suno", "please"
    )

    /**
     * "Jarvis" said with a Bangla, Hindi or Hinglish accent lands on these spellings in the
     * English recognition model, so every one of them has to wake the assistant too.
     */
    private val NAME_ALIASES = setOf(
        "jarvis", "jarbis", "jervis", "jarwis", "zarvis", "zarbis", "javis", "jarvisss",
        "jobis", "jabis", "javed", "jarvi", "jarvish", "jarvissss"
    )

    /** Only aliases present in the packaged lexicon belong in the decoder grammar. */
    fun recognitionPhrases(wakePhrases: Set<String>): Set<String> =
        wakePhrases + wakePhrases.filter { Regex("\\bjarvis\\b").containsMatchIn(it) }
            .map { it.replace(Regex("\\bjarvis\\b"), "jervis") }

    /** The actual trigger words: "jarvis", a custom wake word, or the owner's name. */
    fun triggerWords(wakePhrases: Set<String>): Set<String> {
        val base = wakePhrases.flatMap { it.split(" ") }.toSet() - FILLERS
        // The aliases only apply while "jarvis" itself is still a wake word, so renaming the
        // assistant to something else never keeps the old name alive.
        return if (base.contains("jarvis")) (base + NAME_ALIASES) - FILLERS else base
    }

    fun containsWakeWord(words: List<String>, wakePhrases: Set<String>): Boolean {
        val triggers = triggerWords(wakePhrases)
        return words.any { it in triggers }
    }

    fun confidence(triggers: Set<String>, wordConfidences: Map<String, Double>): Double =
        triggers.maxOfOrNull { wordConfidences[it] ?: 0.0 } ?: 0.0
}