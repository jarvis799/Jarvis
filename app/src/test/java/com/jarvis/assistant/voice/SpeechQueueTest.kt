package com.jarvis.assistant.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The lane is the only thing standing between the assistant and two voices talking at once, so the
 * ordering, the duplicate drop and the barge-in behaviour are pinned down here.
 */
class SpeechQueueTest {

    private fun item(text: String, owner: String = "assistant", generation: Long = 0L): SpeechItem =
        SpeechItem(id = text, text = text, owner = owner, generation = generation)

    @Test
    fun linesComeOutInTheOrderTheyWereQueued() {
        val queue = SpeechQueue()
        queue.offer(item("first"))
        queue.offer(item("second"))
        queue.offer(item("third"))

        assertEquals("first", queue.poll()?.text)
        assertEquals("second", queue.poll()?.text)
        assertEquals("third", queue.poll()?.text)
        assertNull(queue.poll())
    }

    @Test
    fun anImmediateDuplicateIsNotQueuedTwice() {
        val queue = SpeechQueue()
        assertTrue(queue.offer(item("Ji sir, Gemini open ho gaya.")))
        assertFalse(queue.offer(item("Ji sir, Gemini open ho gaya.")))
        assertEquals(1, queue.size())
    }

    @Test
    fun theSameLineForADifferentOwnerIsStillQueued() {
        val queue = SpeechQueue()
        assertTrue(queue.offer(item("Opening Gemini.", owner = "assistant")))
        assertTrue(queue.offer(item("Opening Gemini.", owner = "system")))
        assertEquals(2, queue.size())
    }

    @Test
    fun theLaneIsBoundedSoAFastModelCannotPileUpAudio() {
        val queue = SpeechQueue(capacity = 2)
        assertTrue(queue.offer(item("one")))
        assertTrue(queue.offer(item("two")))
        assertFalse(queue.offer(item("three")))
        assertEquals(2, queue.size())
    }

    @Test
    fun bargeInDropsEverythingQueuedForTheOldTurn() {
        val queue = SpeechQueue()
        queue.offer(item("old line one"))
        queue.offer(item("old line two"))

        val dropped = queue.bump()

        assertEquals(2, dropped.size)
        assertTrue(queue.isEmpty())
        assertEquals(1L, queue.generation)
    }

    @Test
    fun aLineFromAnOlderGenerationIsRecognisableAsStale() {
        val queue = SpeechQueue()
        val old = item("from the previous turn", generation = queue.generation)
        queue.bump()

        assertEquals(0L, old.generation)
        assertFalse(old.generation == queue.generation)
    }

    @Test
    fun requeueAtTheHeadKeepsTheFirstLineFirst() {
        val queue = SpeechQueue()
        queue.offer(item("second"))
        queue.offerFront(item("first"))

        assertEquals("first", queue.poll()?.text)
        assertEquals("second", queue.poll()?.text)
    }
}
