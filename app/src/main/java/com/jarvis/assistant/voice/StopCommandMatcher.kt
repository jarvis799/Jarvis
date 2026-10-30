package com.jarvis.assistant.voice

/**
 * Context-aware STOP detection (English, Bengali, Hindi, romanised Bangla/Hindi).
 *
 * Rules that keep ordinary conversation and device commands safe:
 *  - a named stop ("jarvis stop", "jarvis থামো", "jarvis ruko") always stops;
 *  - a bare stop ("stop", "থামো", "रुको") only counts while work is actually in progress,
 *    so saying "stop" in a sentence is not an interruption;
 *  - a stop that carries a device object ("ফোন বন্ধ করো", "stop the music", "wifi bondho
 *    koro") is a device command, not a voice interruption, and is left to the command layer;
 *  - long sentences are never treated as a stop command.
 */
object StopCommandMatcher {

    private val NAMED_STOP = listOf(
        "jarvis stop", "jarvis, stop", "stop jarvis", "stop, jarvis",
        "jarvis thamo", "jarvis থামো", "jarvis ruk", "jarvis ruko", "jarvis ruk jao",
        "jarvis band karo", "jarvis bondho koro", "जार्विस रुको", "জার্ভিস থামো"
    )

    private val BARE_STOP = setOf(
        "stop", "stop it", "stop now", "stop please", "please stop",
        "thamo", "thamao", "tham", "thamo please", "bondho koro", "bondho koro please", "band karo",
        "থামো", "থাম", "থামো প্লিজ", "বন্ধ করো", "বন্ধ কর",
        "रुको", "रुक", "रुको प्लीज़", "बंद करो", "बंद कर", "बस करो", "बस"
    )

    /** Words that turn a stop into a device/media command instead of a voice interruption. */
    private val DEVICE_OBJECTS = listOf(
        "music", "song", "gan", "गाना", "म्यूजिक", "গান", "মিউজিক", "video", "ভিডিও",
        "alarm", "timer", "alarm", "phone", "fon", "ফোন", "wifi", "wi-fi", "ওয়াইফাই",
        "bluetooth", "ব্লুটুথ", "call", "কল", "media", "playback", "podcast"
    )

    /**
     * @param workActive true while Jarvis is thinking or speaking — the only context where a
     *   bare stop is meaningful.
     */
    fun isStopCommand(text: String, workActive: Boolean): Boolean {
        val normalized = normalize(text)
        if (normalized.isEmpty()) return false
        if (normalized.split(' ').size > 4) return false
        if (DEVICE_OBJECTS.any { normalized.contains(it) }) return false
        if (NAMED_STOP.any { normalized == it || normalized.startsWith("$it ") || normalized.endsWith(" $it") }) return true
        if (normalized in BARE_STOP) return workActive
        return false
    }

    private fun normalize(text: String): String {
        val sb = StringBuilder(text.length)
        var lastSpace = false
        for (ch in text.lowercase()) {
            if (ch.isLetterOrDigit() || ch == '-') {
                sb.append(ch)
                lastSpace = false
            } else if (!lastSpace) {
                sb.append(' ')
                lastSpace = true
            }
        }
        return sb.toString().trim()
    }
}