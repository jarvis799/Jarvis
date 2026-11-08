package com.jarvis.assistant.voice

import java.util.Locale

/** One speakable line waiting in Jarvis's single voice lane. */
data class SpeechItem(
    val id: String,
    val text: String,
    val owner: String,
    val generation: Long,
    val locale: Locale? = null,
    val onFinished: ((Boolean) -> Unit)? = null
)

/**
 * The single ordered lane every spoken word goes through.
 *
 * One queue means one voice: two subsystems can no longer speak at the same time, and no line is
 * ever reordered. Bumping the generation is a barge-in - everything queued for the old turn is
 * dropped instead of being spoken later, which is how old replies used to resurface.
 */
class SpeechQueue(private val capacity: Int = 64) {

    private val items = ArrayDeque<SpeechItem>()

    var generation: Long = 0L
        private set

    /** Returns false when the line is an immediate duplicate or the lane is full. */
    fun offer(item: SpeechItem): Boolean {
        val last = items.lastOrNull()
        if (last != null && last.text == item.text && last.owner == item.owner) return false
        if (items.size >= capacity) return false
        items.addLast(item)
        return true
    }

    /** Puts a line back at the head, used when the engine is not ready yet. */
    fun offerFront(item: SpeechItem) {
        items.addFirst(item)
        while (items.size > capacity) items.removeLastOrNull()
    }

    fun poll(): SpeechItem? = items.removeFirstOrNull()

    fun clear(): List<SpeechItem> {
        val dropped = items.toList()
        items.clear()
        return dropped
    }

    /** A new turn started: drop what was queued for the old one. */
    fun bump(): List<SpeechItem> {
        generation += 1
        return clear()
    }

    fun size(): Int = items.size

    fun isEmpty(): Boolean = items.isEmpty()
}
