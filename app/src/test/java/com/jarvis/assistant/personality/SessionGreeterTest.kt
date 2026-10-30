package com.jarvis.assistant.personality

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionGreeterTest {

    @Test
    fun formatsOneHourNineteenMinutes() {
        val ms = (1 * 60 + 19) * 60 * 1000L
        assertEquals("1 hour 19 minutes", SessionGreeter.formatDuration(ms))
    }

    @Test
    fun formatsDaysWithoutMinutesNoise() {
        val ms = (2 * 24 * 60 + 30) * 60 * 1000L
        assertEquals("2 days 30 minutes", SessionGreeter.formatDuration(ms))
    }

    @Test
    fun briefAppSwitchProducesNoGreeting() {
        assertEquals("", SessionGreeter.greeting(elapsedMs = 60 * 1000L))
    }

    @Test
    fun longAbsenceMentionsRealElapsedTime() {
        val greeting = SessionGreeter.greeting((1 * 60 + 19) * 60 * 1000L, language = "en")
        assertTrue(greeting.contains("1 hour 19 minutes"))
    }

    @Test
    fun missingTimestampNeverInventsDuration() {
        val greeting = SessionGreeter.greeting(elapsedMs = -1L, language = "en")
        assertFalse(greeting.contains("minute"))
        assertFalse(greeting.contains("hour"))
        assertTrue(greeting.isNotBlank())
    }

    @Test
    fun bengaliAndHindiGreetingsStayInTheirLanguage() {
        val bn = SessionGreeter.greeting(90 * 60 * 1000L, language = "Bengali")
        assertTrue(bn.contains("ফিরলেন"))
        val hi = SessionGreeter.greeting(90 * 60 * 1000L, language = "Hindi")
        assertTrue(hi.contains("बाद लौटे"))
    }

    @Test
    fun topicIsOnlyMentionedWhenStored() {
        val withTopic = SessionGreeter.greeting(90 * 60 * 1000L, topic = "the release build", language = "en")
        assertTrue(withTopic.contains("the release build"))
        val withoutTopic = SessionGreeter.greeting(90 * 60 * 1000L, topic = "", language = "en")
        assertFalse(withoutTopic.contains("Last time we were on"))
    }
}