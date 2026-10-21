package com.jarvis.assistant.automation.command

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechAddressFilterTest {

    @Test
    fun wakeWordAlwaysCountsAsAddressed() {
        assertTrue(SpeechAddressFilter.isLikelyAddressedToAssistant("jarvis ei gaan ta band koro", false, false))
    }

    @Test
    fun ongoingExchangeCountsAsAddressed() {
        assertTrue(SpeechAddressFilter.isLikelyAddressedToAssistant("haan thik ache", true, false))
    }

    @Test
    fun knownCommandCountsAsAddressed() {
        assertTrue(SpeechAddressFilter.isLikelyAddressedToAssistant("set volume to fifty", false, true))
    }

    @Test
    fun overheardChatterIsIgnored() {
        assertFalse(SpeechAddressFilter.isLikelyAddressedToAssistant(
            "ami kalke bazar e jabo tarpor dost er sathe cinema dekhbo", false, false))
    }

    @Test
    fun blankSpeechIsNeverAddressed() {
        assertFalse(SpeechAddressFilter.isLikelyAddressedToAssistant("   ", false, true))
    }
}
