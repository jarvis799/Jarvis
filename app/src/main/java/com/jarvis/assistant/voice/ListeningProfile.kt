package com.jarvis.assistant.voice

/**
 * Listening profiles, as the PRD asks: the same microphone and the same recogniser, but
 * different acceptance rules. The values are deliberately conservative - they are about how
 * hard it is to get Jarvis to act, not about understanding speech perfectly.
 */
enum class ListeningProfile(
    val key: String,
    val label: String,
    /** Multiplier for acoustic confidence thresholds; above 1 makes the gate stricter. */
    val wakeConfidenceFactor: Float,
    /** Shortest utterance worth acting on, in milliseconds. */
    val minimumUtteranceMs: Int,
    /** True when a bare utterance (no wake word) is allowed to start a turn. */
    val acceptsSpeechWithoutWake: Boolean,
    val description: String
) {
    QUIET(
        "quiet", "Quiet room", 0.90f, 100, true,
        "A room with one speaker. The wake word can be softer and follow-ups are accepted."
    ),
    NORMAL(
        "normal", "Normal", 1.00f, 120, true,
        "Everyday use: wake word, follow-ups and interruptions behave as they always have."
    ),
    PUBLIC_PLACE(
        "public", "Public place", 1.30f, 160, false,
        "Traffic, a cafe, a television. Only a clearly spoken wake word with a command starts a turn; nearby conversation is ignored."
    );

    companion object {
        fun from(key: String?): ListeningProfile =
            values().firstOrNull { it.key == key } ?: NORMAL
    }
}
