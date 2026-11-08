package com.jarvis.assistant.voice.hotword

import org.junit.Assert.*
import org.junit.Test

class WakeListenerHealthTest {
    @Test fun startingThreadIsNotProofOfListening() {
        val health = WakeListenerHealth()
        health.begin(0L)
        assertFalse(health.isListening)
        assertFalse(health.isStalled(11_999L))
        assertTrue(health.isStalled(12_000L))
    }

    @Test fun audioCallbacksConfirmAndKeepTheWorkerAlive() {
        val health = WakeListenerHealth()
        val token = health.begin(100L)
        assertTrue(health.onAudioCallback(token, 5_000L))
        assertTrue(health.isListening)
        assertFalse(health.isStalled(16_999L))
        assertTrue(health.isStalled(17_000L))
    }

    @Test fun stoppingInvalidatesQueuedCallbacks() {
        val health = WakeListenerHealth()
        val token = health.begin(0L)
        health.stop()
        assertFalse(health.onAudioCallback(token, 1_000L))
        assertFalse(health.isCurrent(token))
        assertFalse(health.isListening)
        assertFalse(health.isStalled(99_000L))
    }

    @Test fun oldCallbackCannotKeepRestartedWorkerAlive() {
        val health = WakeListenerHealth()
        val old = health.begin(0L)
        health.stop()
        val current = health.begin(2_000L)
        assertNotEquals(old, current)
        assertFalse(health.onAudioCallback(old, 13_000L))
        assertTrue(health.isCurrent(current))
        assertTrue(health.isStalled(14_000L))
    }

    @Test fun restartRequiresFreshAudioConfirmation() {
        val health = WakeListenerHealth()
        val old = health.begin(0L)
        health.onAudioCallback(old, 1L)
        val current = health.begin(2L)
        assertFalse(health.isListening)
        assertFalse(health.onAudioCallback(old, 3L))
        assertTrue(health.onAudioCallback(current, 4L))
        assertTrue(health.isListening)
    }

    @Test fun retryBackoffIsBounded() {
        assertEquals(1_500L, WakeListenerHealth.retryDelay(0))
        assertEquals(3_000L, WakeListenerHealth.retryDelay(1))
        assertEquals(24_000L, WakeListenerHealth.retryDelay(4))
        assertEquals(30_000L, WakeListenerHealth.retryDelay(5))
        assertEquals(30_000L, WakeListenerHealth.retryDelay(Int.MAX_VALUE))
    }
}
