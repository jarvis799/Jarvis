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

    /** The actual trigger words: "jarvis", a custom wake word, or the owner's name. */
    fun triggerWords(wakePhrases: Set<String>): Set<String> =
        wakePhrases.flatMap { it.split(" ") }.toSet() - FILLERS

    fun containsWakeWord(words: List<String>, wakePhrases: Set<String>): Boolean {
        val triggers = triggerWords(wakePhrases)
        return words.any { it in triggers }
    }

    fun confidence(triggers: Set<String>, wordConfidences: Map<String, Double>): Double =
        triggers.maxOfOrNull { wordConfidences[it] ?: 0.0 } ?: 0.0
}