package com.jarvis.assistant.voice

import org.junit.Assert.assertEquals
import org.junit.Test

class NoiseConfidenceRegressionTest {
    @Test fun zeroIsNotTreatedAsMissingConfidence() {
        assertEquals(SpeechConfidencePolicy.Verdict.UNINTELLIGIBLE,
            SpeechConfidencePolicy().evaluate("call dad", 0f))
    }
    @Test fun unavailableConfidenceIsNotFabricated() {
        assertEquals(SpeechConfidencePolicy.Verdict.CLEAR,
            SpeechConfidencePolicy().evaluate("call dad", -1f))
    }
}