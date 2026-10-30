package com.jarvis.assistant.voice

import org.junit.Assert.*
import org.junit.Test

class VoiceActivityGateTest {

    private fun gate(sustainedMs: Int = 150) =
        VoiceActivityGate(sustainedMs = sustainedMs, frameMs = 20, silenceHangoverMs = 400)

    @Test fun requiresSustainedSpeechBeforeStarting() {
        val g = gate()
        repeat(7) { assertEquals(VoiceActivityGate.Event.NONE, g.onFrame(0.2f, false)) }
        assertEquals(VoiceActivityGate.Event.SPEECH_STARTED, g.onFrame(0.2f, false))
    }

    @Test fun isolatedBurstDoesNotOpenGate() {
        val g = gate()
        assertEquals(VoiceActivityGate.Event.NONE, g.onFrame(0.9f, false))
        assertEquals(VoiceActivityGate.Event.NONE, g.onFrame(0.0f, false))
        assertEquals(VoiceActivityGate.Event.NONE, g.onFrame(0.9f, false))
        assertFalse(g.isSpeaking)
    }

    @Test fun silenceHangoverEndsUtterance() {
        val g = gate()
        repeat(8) { g.onFrame(0.2f, false) }
        assertTrue(g.isSpeaking)
        // A short pause inside a sentence must not end the utterance.
        repeat(5) { assertEquals(VoiceActivityGate.Event.NONE, g.onFrame(0.0f, false)) }
        assertTrue(g.isSpeaking)
        repeat(20) { g.onFrame(0.0f, false) }
        assertFalse(g.isSpeaking)
    }

    @Test fun bargeInNeedsLouderSpeechThanNormalListening() {
        val listening = gate()
        assertEquals(VoiceActivityGate.Event.SPEECH_STARTED, run { repeat(7) { listening.onFrame(0.03f, false) }; listening.onFrame(0.03f, false) })

        val whileSpeaking = gate()
        repeat(20) { assertEquals(VoiceActivityGate.Event.NONE, whileSpeaking.onFrame(0.03f, true)) }
        assertFalse(whileSpeaking.isSpeaking)
    }

    @Test fun resetClearsState() {
        val g = gate()
        repeat(8) { g.onFrame(0.2f, false) }
        assertTrue(g.isSpeaking)
        g.reset()
        assertFalse(g.isSpeaking)
        assertEquals(VoiceActivityGate.Event.NONE, g.onFrame(0.2f, false))
    }
}