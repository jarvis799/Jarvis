package com.jarvis.assistant.personality

import com.jarvis.assistant.data.model.GeminiConstants
import com.jarvis.assistant.util.PromptGenerator
import org.junit.Assert.*
import org.junit.Test

class AdultEducationPromptTest {
    private fun prompt(enabled: Boolean) = PromptGenerator.generateSystemPrompt(
        personality = GeminiConstants.PERSONALITY_GIRLFRIEND,
        userName = "Test operator",
        language = "Bengali",
        personalityProfile = PersonalityProfile(voiceMode = VoiceMode.GIRLFRIEND),
        adultEducationEnabled = enabled
    )

    @Test fun livePromptCarriesEnabledStateAfterPersonaInstructions() {
        val output = prompt(true)
        assertTrue(output.trimEnd().endsWith(AdultEducationPolicy.promptBlock(true).trimEnd()))
        assertTrue(output.contains("Mode: ON (saved setting)"))
        assertTrue(output.contains("override romantic persona"))
    }

    @Test fun disablingDoesNotLeaveEnabledInstructionsInTheNextPrompt() {
        val output = prompt(false)
        assertTrue(output.trimEnd().endsWith(AdultEducationPolicy.promptBlock(false).trimEnd()))
        assertTrue(output.contains("Mode: OFF (saved setting)"))
        assertFalse(output.contains("Mode: ON (saved setting)"))
        assertFalse(output.contains("[EDUCATIONAL RESPONSE RULES"))
    }
}