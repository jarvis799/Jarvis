package com.jarvis.assistant.voice

import org.junit.Assert.*
import org.junit.Test

class VoiceTurnCoordinatorTest {

    @Test fun tokensAdvancePerTurn() {
        val turns = VoiceTurnCoordinator()
        val first = turns.begin()
        assertTrue(turns.isCurrent(first))
        val second = turns.begin()
        assertFalse(turns.isCurrent(first))
        assertTrue(turns.isCurrent(second))
    }

    @Test fun invalidationMakesEveryOutstandingTokenStale() {
        val turns = VoiceTurnCoordinator()
        val token = turns.begin()
        turns.invalidate()
        assertFalse(turns.isCurrent(token))
    }

    @Test fun lateCallbackAfterInterruptionIsRejected() {
        val turns = VoiceTurnCoordinator()
        val token = turns.begin()
        turns.invalidate()
        // A late TTS/LLM callback from the cancelled turn must not be allowed through.
        assertFalse("late audio must be dropped", turns.isCurrent(token))
    }

    @Test fun newTurnAfterInterruptionIsAccepted() {
        val turns = VoiceTurnCoordinator()
        turns.begin()
        turns.invalidate()
        val fresh = turns.begin()
        assertTrue(turns.isCurrent(fresh))
    }
}