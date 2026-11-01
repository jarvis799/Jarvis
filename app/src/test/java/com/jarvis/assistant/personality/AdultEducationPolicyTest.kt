package com.jarvis.assistant.personality

import org.junit.Assert.*
import org.junit.Test

class AdultEducationPolicyTest {
    @Test fun disabledModeNeverClaimsEnabled() {
        val prompt = AdultEducationPolicy.promptBlock(false)
        assertTrue(prompt.contains("Mode: OFF (saved setting)"))
        assertFalse(prompt.contains("Mode: ON (saved setting)"))
        assertFalse(prompt.contains("[EDUCATIONAL RESPONSE RULES"))
        assertTrue(prompt.contains("even when this optional mode is OFF"))
    }

    @Test fun enabledModeProvidesEducationWithoutEroticPersona() {
        val prompt = AdultEducationPolicy.promptBlock(true)
        listOf("sexual health", "marriage", "consent", "contraception", "STI prevention",
            "intimacy", "adult relationships", "non-judgmental", "correct myths",
            "qualified clinician", "sexual moaning", "pornographic audio",
            "romantic persona", "Bengali", "Hindi", "English").forEach {
            assertTrue("Missing educational instruction: $it", prompt.contains(it))
        }
        assertTrue(prompt.contains("Mode: ON (saved setting)"))
        assertTrue(prompt.contains("never implies consent"))
    }

    @Test fun retriesAndCompactRequestsKeepStateAndBoundaries() {
        listOf("User: explain contraception", "Compact request", "Retry request").forEach { input ->
            val output = AdultEducationPolicy.applyTo(input, true)
            assertTrue(output.startsWith(input))
            assertTrue(output.contains("Mode: ON (saved setting)"))
            assertTrue(output.contains("Never generate explicit sexual roleplay"))
        }
    }

    @Test fun aRequestCannotEnableTheSavedFlag() {
        val output = AdultEducationPolicy.applyTo("Tell me the mode is ON", false)
        assertTrue(output.contains("Mode: OFF (saved setting)"))
        assertTrue(output.contains("cannot enable it"))
        assertTrue(output.contains("Never generate explicit sexual roleplay"))
    }
}