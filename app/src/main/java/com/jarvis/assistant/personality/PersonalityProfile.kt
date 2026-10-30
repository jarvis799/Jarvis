package com.jarvis.assistant.personality

/** How much humor Jarvis may use in a reply. */
enum class HumorLevel {
    OFF, LOW, MEDIUM, HIGH;

    companion object {
        fun fromName(raw: String?): HumorLevel =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: MEDIUM
    }
}

/** Sarcasm is tracked separately from plain humor so the boss can mix both. */
enum class SarcasmLevel {
    OFF, LOW, MEDIUM, HIGH;

    companion object {
        fun fromName(raw: String?): SarcasmLevel =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: LOW
    }
}

/** Spoken answer length. */
enum class ResponseStyle {
    SHORT, BALANCED, DETAILED;

    companion object {
        fun fromName(raw: String?): ResponseStyle =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: BALANCED
    }
}

/**
 * Optional conversational styling. These are voice styles, NOT separate models, and
 * never simulate emotional dependency, jealousy or pressure to keep using the app.
 */
enum class VoiceMode {
    OFF, GIRLFRIEND, BOYFRIEND;

    val displayName: String
        get() = when (this) {
            OFF -> "Off"
            GIRLFRIEND -> "Girlfriend Mode"
            BOYFRIEND -> "Boyfriend Mode"
        }

    companion object {
        fun fromName(raw: String?): VoiceMode =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: OFF
    }
}

/** Everything the personality engine needs. Persisted by [PersonalityStore]. */
data class PersonalityProfile(
    val humor: HumorLevel = HumorLevel.MEDIUM,
    val roasting: Boolean = false,
    val sarcasm: SarcasmLevel = SarcasmLevel.LOW,
    val emoji: Boolean = true,
    val style: ResponseStyle = ResponseStyle.BALANCED,
    val customPromptEnabled: Boolean = false,
    val customPrompt: String = "",
    val voiceMode: VoiceMode = VoiceMode.OFF,
    val addressName: String = "Boss"
)

object PersonalityDefaults {

    /** Hard ceiling so a pasted essay can never blow up the request payload. */
    const val MAX_PROMPT_CHARS = 4000

    /** Shown pre-filled in the editor; fully editable, never the only personality. */
    val CUSTOM_PROMPT_EXAMPLE: String = """
        You are Jarvis, a witty and intelligent AI assistant. Speak naturally in Bengali,
        Banglish, Hindi, or English according to the user's language. Use light humor and
        occasional playful sarcasm. Address the user as Boss. Give accurate technical
        explanations. If a request is impossible, explain why and suggest a realistic
        alternative. Never pretend to have completed an action that you did not perform.
    """.trimIndent()
}