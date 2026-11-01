package com.jarvis.assistant.voice

import org.junit.Assert.*
import org.junit.Test

class LiveInputGateTest {
    private val frame = ByteArray(640)
    @Test fun quietNoiseIsNotUploaded() {
        val gate = LiveInputGate()
        repeat(150) { assertTrue(gate.accept(frame, .01f).chunks.isEmpty()) }
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
        repeat(8) { assertTrue(gate.accept(frame, .2f).chunks.isEmpty()) }
        assertEquals(9, gate.accept(frame, .2f).chunks.size)
    }
    @Test fun silenceClosesStreamOnce() {
        val gate = LiveInputGate()
        repeat(10) { gate.accept(frame, .2f) }
        repeat(29) { assertFalse(gate.accept(frame, .0f).ended) }
        assertTrue(gate.accept(frame, .0f).ended)
        assertFalse(gate.accept(frame, .0f).ended)
    }
    @Test fun resetDropsOldAudio() {
        val gate = LiveInputGate()
        repeat(10) { gate.accept(frame, .2f) }
        gate.reset()
        assertTrue(gate.accept(frame, .01f).chunks.isEmpty())
    }
}