package com.jarvis.assistant.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechTextTest {

    @Test
    fun markdownEmphasisIsDropped() {
        assertEquals("Hello Boss, done.", SpeechText.clean("**Hello Boss**, done."))
    }

    @Test
    fun codeFenceIsNotReadAloud() {
        val spoken = SpeechText.clean("Here is the code:\n\u0060\u0060\u0060kotlin\nval x = 1\n\u0060\u0060\u0060\nDone.")
        assertFalse(spoken.contains("val x"))
        assertTrue(spoken.contains("Done."))
    }

    @Test
    fun linksKeepTheirLabel() {
        assertEquals("Open the site now.", SpeechText.clean("Open [the site](https://example.com) now."))
        assertEquals("Read more.", SpeechText.clean("Read more https://example.com/a?b=1."))
    }

    @Test
    fun bulletsAndHeadingsAreDropped() {
        assertEquals("Steps One. Two.", SpeechText.clean("## Steps\n- One.\n* Two."))
    }

    @Test
    fun emojiIsDroppedButBengaliSurvives() {
        val spoken = SpeechText.clean("\u09b9\u09cd\u09af\u09be\u09b2\u09cb Boss \ud83d\ude00 \u0986\u09ae\u09bf \u09aa\u09cd\u09b0\u09b8\u09cd\u09a4\u09c1\u09a4\u0964")
        assertFalse(spoken.contains("\ud83d\ude00"))
        assertTrue(spoken.contains("\u09b9\u09cd\u09af\u09be\u09b2\u09cb"))
        assertTrue(spoken.contains("\u09aa\u09cd\u09b0\u09b8\u09cd\u09a4\u09c1\u09a4"))
    }

    @Test
    fun blankStaysBlank() {
        assertEquals("", SpeechText.clean("   "))
    }
}
