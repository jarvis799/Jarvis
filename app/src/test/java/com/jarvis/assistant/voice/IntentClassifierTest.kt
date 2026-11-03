package com.jarvis.assistant.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IntentClassifierTest {

    @Test
    fun openingAnAppIsADeviceCommand() {
        val result = IntentClassifier.classify("open YouTube")
        assertEquals(IntentCategory.DEVICE_COMMAND, result.category)
        assertTrue(result.confidence > 0.85f)
    }

    @Test
    fun greetingsAreNotCommands() {
        assertEquals(IntentCategory.AMBIENT_NOISE, IntentClassifier.classify("hmm").category)
        assertEquals(IntentCategory.AMBIENT_NOISE, IntentClassifier.classify("").category)
    }

    @Test
    fun questionGoesToTheConversationRoute() {
        assertEquals(IntentCategory.CONVERSATIONAL_QUERY, IntentClassifier.classify("why is the sky blue?").category)
        assertEquals(IntentCategory.CONVERSATIONAL_QUERY, IntentClassifier.classify("explain quantum computing").category)
    }

    @Test
    fun correctionIsRecognised() {
        assertEquals(IntentCategory.CORRECTION, IntentClassifier.classify("no, open Chrome").category)
        assertEquals(IntentCategory.CORRECTION, IntentClassifier.classify("no I meant Chrome").category)
    }

    @Test
    fun twoActionsAreMultiAction() {
        val result = IntentClassifier.classify("open YouTube and turn the volume up")
        assertEquals(IntentCategory.MULTI_ACTION_COMMAND, result.category)
        assertEquals(2, result.actionCount)
    }

    @Test
    fun televisionNarrationIsBackgroundSpeech() {
        assertEquals(
            IntentCategory.BACKGROUND_SPEECH,
            IntentClassifier.classify("next up, breaking news from the studio").category
        )
    }

    @Test
    fun riskyVerbWithoutTargetAsksInsteadOfActing() {
        assertEquals(IntentCategory.UNCERTAIN_INTENT, IntentClassifier.classify("call").category)
    }

    @Test
    fun followUpIsDetected() {
        assertEquals(IntentCategory.FOLLOW_UP, IntentClassifier.classify("what about tomorrow").category)
    }

    @Test
    fun wakePrefixIsStripped() {
        assertEquals(IntentCategory.DEVICE_COMMAND, IntentClassifier.classify("hey jarvis, open the camera").category)
    }
}
