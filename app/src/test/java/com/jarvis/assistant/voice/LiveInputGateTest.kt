package com.jarvis.assistant.voice

import org.junit.Assert.*
import org.junit.Test

class LiveInputGateTest {
    private val frame = ByteArray(640) // 20 ms at 16 kHz mono PCM16

    @Test fun quietNoiseIsNotUploaded() {
        val gate = LiveInputGate()
        repeat(150) { assertTrue(gate.accept(frame, .01f).chunks.isEmpty()) }
    }

    @Test fun steadyBackgroundNoiseIsNotUploaded() {
        val gate = LiveInputGate()
        // A fan / TV hum sits flat around 0.09 and must never be treated as speech.
        repeat(150) { assertTrue(gate.accept(frame, .09f).chunks.isEmpty()) }
    }

    @Test fun isolatedClicksDoNotAccumulate() {
        val gate = LiveInputGate()
        repeat(30) {
            assertTrue(gate.accept(frame, .5f).chunks.isEmpty())
            assertTrue(gate.accept(frame, .01f).chunks.isEmpty())
        }
    }

    @Test fun sustainedSpeechIncludesItsOnset() {
        val gate = LiveInputGate()
        repeat(12) { assertTrue(gate.accept(frame, .2f).chunks.isEmpty()) }
        assertEquals(13, gate.accept(frame, .2f).chunks.size)
    }

    @Test fun silenceClosesStreamOnce() {
        val gate = LiveInputGate()
        repeat(13) { gate.accept(frame, .2f) }
        repeat(34) { assertFalse(gate.accept(frame, .0f).ended) }
        assertTrue(gate.accept(frame, .0f).ended)
        assertFalse(gate.accept(frame, .0f).ended)
    }

    @Test fun resetDropsOldAudio() {
        val gate = LiveInputGate()
        repeat(13) { gate.accept(frame, .2f) }
        gate.reset()
        assertTrue(gate.accept(frame, .01f).chunks.isEmpty())
    }
}