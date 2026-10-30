package com.jarvis.assistant.voice

/**
 * Decides whether a transcript is safe to send to the LLM.
 *
 * Honesty rules (from the PRD, enforced here so they cannot drift at call sites):
 *  - a missing confidence score is NOT treated as low confidence, and no score is ever
 *    invented — when the recogniser supplies none, only measurable signals are used
 *    (empty transcript, engine error, very short utterance, conflicting hypotheses);
 *  - clarification is bounded, so Jarvis cannot loop asking the user to repeat;
 *  - this class only judges the *transcript*. Uncertainty inside the LLM's answer is a
 *    different problem and is not handled here.
 */
class SpeechConfidencePolicy(
    private val unintelligibleBelow: Float = 0.55f,
    private val uncertainBelow: Float = 0.80f,
    private val maxClarifications: Int = 2
) {

    enum class Verdict { CLEAR, UNCERTAIN, UNINTELLIGIBLE }

    private var asked = 0

    /** How many clarification prompts have already been spoken this session. */
    val attempts: Int get() = asked

    fun reset() {
        asked = 0
    }

    fun shouldAsk(): Boolean = asked < maxClarifications

    /** Records that a clarification prompt was actually spoken. */
    fun onClarificationSpoken() {
        asked++
    }

    /**
     * @param transcript best hypothesis, as returned by the recogniser
     * @param confidence score in 0..1 when the engine provides one, otherwise null
     * @param alternatives further hypotheses; used as an ambiguity signal
     * @param engineError true when the recogniser reported an error (no match, timeout)
     */
    fun evaluate(
        transcript: String,
        confidence: Float?,
        alternatives: List<String> = emptyList(),
        engineError: Boolean = false
    ): Verdict {
        val text = transcript.trim()
        if (engineError || text.isEmpty()) return Verdict.UNINTELLIGIBLE

        val score = confidence?.takeIf { it.isFinite() && it > 0f }
        if (score != null) {
            if (score < unintelligibleBelow) return Verdict.UNINTELLIGIBLE
            if (score < uncertainBelow) return Verdict.UNCERTAIN
            return Verdict.CLEAR
        }

        // No score from the engine: judge only what is measurable.
        if (text.length < 2) return Verdict.UNINTELLIGIBLE
        val usefulAlternatives = alternatives.filter { it.isNotBlank() && it.trim() != text }
        if (usefulAlternatives.isNotEmpty() && conflicts(text, usefulAlternatives.first())) {
            return Verdict.UNCERTAIN
        }
        return Verdict.CLEAR
    }

    fun clarification(language: String, attempt: Int): String {
        val lang = language.trim().lowercase()
        val bengali = lang.contains("bengali") || lang == "bn" || lang.startsWith("bn-") || lang.contains("bangla")
        val hindi = lang.contains("hindi") || lang == "hi" || lang.startsWith("hi-")
        return when {
            bengali -> if (attempt >= 1) "দুঃখিত, এখনও পরিষ্কার বুঝতে পারিনি। একটু জোরে, ধীরে বলবে?"
                else "দুঃখিত, কথাটা পরিষ্কার বুঝতে পারিনি। আরেকবার বলবে?"
            hindi -> if (attempt >= 1) "माफ़ कीजिए, अभी भी स्पष्ट नहीं सुनाई दिया। थोड़ा धीरे और साफ़ बोलिए।"
                else "माफ़ कीजिए, आपकी बात स्पष्ट रूप से समझ नहीं आई। कृपया दोबारा बोलिए।"
            else -> if (attempt >= 1) "Sorry, I still couldn't catch that. Please say it a little slower."
                else "Sorry, I couldn't understand that clearly. Could you repeat it?"
        }
    }

    /** Localised "I stopped asking" message once the retry budget is spent. */
    fun giveUpMessage(language: String): String {
        val lang = language.trim().lowercase()
        return when {
            lang.contains("bengali") || lang == "bn" || lang.startsWith("bn-") -> "বুঝতে পারিনি। চাইলে লিখে পাঠাও।"
            lang.contains("hindi") || lang == "hi" || lang.startsWith("hi-") -> "समझ नहीं आया। चाहें तो लिखकर भेजिए।"
            else -> "I couldn't catch that. You can type it instead."
        }
    }

    private fun conflicts(a: String, b: String): Boolean {
        val wordsA = words(a)
        val wordsB = words(b)
        if (wordsA.isEmpty() || wordsB.isEmpty()) return false
        val common = wordsA.intersect(wordsB).size.toFloat()
        val larger = maxOf(wordsA.size, wordsB.size).toFloat()
        return common / larger < 0.34f
    }

    private fun words(text: String): Set<String> =
        text.lowercase()
            .split(Regex("[^\\p{L}\\p{N}]+"))
            .filter { it.length > 1 }
            .toSet()
}