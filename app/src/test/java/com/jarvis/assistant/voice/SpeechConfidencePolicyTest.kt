package com.jarvis.assistant.voice

import org.junit.Assert.*
import org.junit.Test

class SpeechConfidencePolicyTest {

    @Test fun blankTranscriptIsUnintelligible() {
        val policy = SpeechConfidencePolicy()
        assertEquals(SpeechConfidencePolicy.Verdict.UNINTELLIGIBLE, policy.evaluate("  ", null))
    }

    @Test fun engineErrorIsUnintelligible() {
        val policy = SpeechConfidencePolicy()
        assertEquals(SpeechConfidencePolicy.Verdict.UNINTELLIGIBLE, policy.evaluate("hello", null, engineError = true))
    }

    @Test fun missingScoreIsNotTreatedAsLowConfidence() {
        val policy = SpeechConfidencePolicy()
        assertEquals(SpeechConfidencePolicy.Verdict.CLEAR, policy.evaluate("আমার ফোনের ওয়াইফাই চালু করো", null))
    }

    @Test fun lowScoreRequestsClarification() {
        val policy = SpeechConfidencePolicy()
        assertEquals(SpeechConfidencePolicy.Verdict.UNINTELLIGIBLE, policy.evaluate("kuch bhi", 0.31f))
        assertEquals(SpeechConfidencePolicy.Verdict.UNCERTAIN, policy.evaluate("kuch bhi", 0.68f))
        assertEquals(SpeechConfidencePolicy.Verdict.CLEAR, policy.evaluate("kuch bhi", 0.94f))
    }

    @Test fun conflictingHypothesesAreUncertain() {
        val policy = SpeechConfidencePolicy()
        val verdict = policy.evaluate("open the camera", null, listOf("close the wifi settings"))
        assertEquals(SpeechConfidencePolicy.Verdict.UNCERTAIN, verdict)
    }

    @Test fun similarHypothesesStayClear() {
        val policy = SpeechConfidencePolicy()
        val verdict = policy.evaluate("open the camera", null, listOf("open the camera app"))
        assertEquals(SpeechConfidencePolicy.Verdict.CLEAR, verdict)
    }

    @Test fun clarificationIsBounded() {
        val policy = SpeechConfidencePolicy(maxClarifications = 2)
        assertTrue(policy.shouldAsk())
        policy.onClarificationSpoken()
        assertTrue(policy.shouldAsk())
        policy.onClarificationSpoken()
        assertFalse(policy.shouldAsk())
        policy.reset()
        assertTrue(policy.shouldAsk())
    }

    @Test fun clarificationMessagesFollowLanguage() {
        val policy = SpeechConfidencePolicy()
        assertTrue(policy.clarification("bengali", 0).contains("বুঝতে পারিনি"))
        assertTrue(policy.clarification("hindi", 0).contains("समझ नहीं आई"))
        assertTrue(policy.clarification("english", 0).contains("couldn't understand"))
    }
}