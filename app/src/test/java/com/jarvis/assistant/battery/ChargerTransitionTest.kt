package com.jarvis.assistant.battery

import org.junit.Assert.*
import org.junit.Test

class ChargerTransitionTest {
    @Test fun initialStickyBroadcastDoesNotSpeak() {
        assertNull(ChargerTransition().accept(true, false, true))
        assertNull(ChargerTransition().accept(true, true, false))
    }
    @Test fun announcesEachChangeOnce() {
        val state = ChargerTransition()
        state.accept(false, false, false)
        assertEquals("charging", state.accept(true, false, true))
        assertNull(state.accept(true, false, true))
        assertEquals("full", state.accept(true, true, false))
        assertNull(state.accept(true, true, false))
        assertEquals("unplugged", state.accept(false, true, false))
        assertNull(state.accept(false, false, false))
        assertEquals("charging", state.accept(true, false, true))
        assertEquals("full", state.accept(true, true, false))
    }
    @Test fun pluggedDoesNotFalselyClaimCharging() {
        val state = ChargerTransition()
        state.accept(false, false, false)
        assertEquals("connected", state.accept(true, false, false))
    }
    @Test fun connectingAtFullWarnsToUnplugOnce() {
        val state = ChargerTransition()
        state.accept(false, true, false)
        assertEquals("full", state.accept(true, true, false))
        assertNull(state.accept(true, true, false))
    }
}