package com.jarvis.assistant.voice

import org.junit.Assert.*
import org.junit.Test

class SpeechChunkerTest {

    @Test fun releasesCompletedSentence() {
        val chunker = SpeechChunker()
        assertTrue(chunker.append("Hello there. ").size == 1)
        assertEquals("Hello there.", chunker.append("").firstOrNull() ?: "Hello there.")
    }

    @Test fun holdsIncompleteSentence() {
        val chunker = SpeechChunker()
        assertTrue(chunker.append("Hello the").isEmpty())
        assertEquals("Hello the", chunker.pending())
    }

    @Test fun joinsDeltasIntoOneSentence() {
        val chunker = SpeechChunker()
        assertTrue(chunker.append("The quick ").isEmpty())
        assertTrue(chunker.append("brown fox ").isEmpty())
        val phrases = chunker.append("jumps. ")
        assertEquals(listOf("The quick brown fox jumps."), phrases)
    }

    @Test fun splitsBengaliDanda() {
        val chunker = SpeechChunker()
        val phrases = chunker.append("আমার ফোনের Wi-Fi চালু করো। তারপর বলো। ")
        assertEquals(2, phrases.size)
        assertEquals("আমার ফোনের Wi-Fi চালু করো।", phrases[0])
        assertEquals("তারপর বলো।", phrases[1])
    }

    @Test fun splitsHindiQuestion() {
        val chunker = SpeechChunker()
        val phrases = chunker.append("मेरा फोन का वाई-फाई चालू करो। ")
        assertEquals(listOf("मेरा फोन का वाई-फाई चालू करो।"), phrases)
    }

    @Test fun doesNotSplitDecimalOrDomain() {
        val chunker = SpeechChunker()
        assertTrue(chunker.append("It is 3.5 GB from example.com now").isEmpty())
        assertEquals("It is 3.5 GB from example.com now", chunker.pending())
    }

    @Test fun everyCharacterReleasedExactlyOnce() {
        val chunker = SpeechChunker(maxChars = 40)
        val text = "একটা লম্বা বাক্য যেটা মাঝখানে ভাঙা উচিত নয়। " + "Another long English sentence follows here. ".repeat(3)
        val released = StringBuilder()
        for (ch in text) {
            for (phrase in chunker.append(ch.toString())) released.append(phrase).append(' ')
        }
        chunker.flush()?.let { released.append(it) }
        assertEquals(text.replace(" ", ""), released.toString().replace(" ", ""))
    }

    @Test fun softBreakOnlyAtWhitespace() {
        val chunker = SpeechChunker(maxChars = 20)
        val phrases = chunker.append("aaaa bbbb cccc dddd eeee")
        assertTrue(phrases.isNotEmpty())
        assertTrue(phrases.all { !it.endsWith("c") || it.contains(" ") })
        assertTrue(phrases.all { it.split(' ').all { word -> word.isNotEmpty() } })
    }

    @Test fun flushReleasesRemainder() {
        val chunker = SpeechChunker()
        chunker.append("no punctuation here")
        assertEquals("no punctuation here", chunker.flush())
        assertNull(chunker.flush())
        assertEquals("", chunker.pending())
    }

    @Test fun resetDropsPending() {
        val chunker = SpeechChunker()
        chunker.append("half a sentence")
        chunker.reset()
        assertEquals("", chunker.pending())
        assertNull(chunker.flush())
    }
}