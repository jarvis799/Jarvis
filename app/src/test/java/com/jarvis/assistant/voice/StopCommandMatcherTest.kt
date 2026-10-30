package com.jarvis.assistant.voice

import org.junit.Assert.*
import org.junit.Test

class StopCommandMatcherTest {

    @Test fun bareStopNeedsActiveWork() {
        assertFalse(StopCommandMatcher.isStopCommand("stop", workActive = false))
        assertTrue(StopCommandMatcher.isStopCommand("stop", workActive = true))
    }

    @Test fun namedStopAlwaysStops() {
        assertTrue(StopCommandMatcher.isStopCommand("Jarvis, stop.", workActive = false))
        assertTrue(StopCommandMatcher.isStopCommand("jarvis থামো", workActive = false))
    }

    @Test fun bengaliVariants() {
        assertTrue(StopCommandMatcher.isStopCommand("থামো", workActive = true))
        assertTrue(StopCommandMatcher.isStopCommand("বন্ধ করো", workActive = true))
    }

    @Test fun hindiVariants() {
        assertTrue(StopCommandMatcher.isStopCommand("रुको", workActive = true))
        assertTrue(StopCommandMatcher.isStopCommand("बंद करो", workActive = true))
    }

    @Test fun deviceCommandIsNotVoiceStop() {
        assertFalse(StopCommandMatcher.isStopCommand("ফোন বন্ধ করো", workActive = true))
        assertFalse(StopCommandMatcher.isStopCommand("stop the music", workActive = true))
        assertFalse(StopCommandMatcher.isStopCommand("wifi bondho koro", workActive = true))
    }

    @Test fun ordinarySentenceIsNotStop() {
        assertFalse(StopCommandMatcher.isStopCommand("please stop by the shop on your way home", workActive = true))
        assertFalse(StopCommandMatcher.isStopCommand("what is the weather today", workActive = true))
    }

    @Test fun emptyInputIsNotStop() {
        assertFalse(StopCommandMatcher.isStopCommand("   ", workActive = true))
    }
}