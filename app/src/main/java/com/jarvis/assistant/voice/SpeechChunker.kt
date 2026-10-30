package com.jarvis.assistant.voice

/**
 * Turns incremental LLM output into speakable phrases.
 *
 * Guarantees relied on by the voice pipeline:
 *  - every character is released exactly once, in order (no duplicated or skipped words);
 *  - a phrase never ends inside a word;
 *  - a phrase is only released on a real sentence boundary, or on a soft break at
 *    whitespace once the buffer grows past [maxChars] (unpunctuated streams).
 *
 * Boundaries cover Bengali/Hindi sentence marks (danda `।`, double danda `॥`) as well as
 * Latin punctuation, so Bengali and Hindi replies are not spoken as one long blob.
 */
class SpeechChunker(private val maxChars: Int = 220) {

    private val buffer = StringBuilder()

    /** Adds a streaming delta and returns every phrase that became speakable. */
    fun append(delta: String): List<String> {
        if (delta.isEmpty()) return emptyList()
        buffer.append(delta)
        val out = ArrayList<String>(2)
        while (true) {
            val cut = cutIndex()
            if (cut <= 0) break
            val phrase = buffer.substring(0, cut).trim()
            buffer.delete(0, cut)
            if (phrase.isNotEmpty()) out.add(phrase)
        }
        return out
    }

    /** Releases whatever is left when the stream ends. */
    fun flush(): String? {
        val rest = buffer.toString().trim()
        buffer.setLength(0)
        return rest.ifEmpty { null }
    }

    fun reset() {
        buffer.setLength(0)
    }

    fun pending(): String = buffer.toString()

    private fun cutIndex(): Int {
        for (i in buffer.indices) {
            val ch = buffer[i]
            if (ch !in SENTENCE_END) continue

            var after = i + 1
            while (after < buffer.length && buffer[after] in TRAILING) after++

            // "3.5", "example.com", "a.b" are not sentence ends: the boundary must be
            // followed by whitespace or by the end of what has arrived so far.
            if (after < buffer.length && !buffer[after].isWhitespace()) continue

            var end = after
            while (end < buffer.length && buffer[end].isWhitespace()) end++
            if (end <= 0) continue
            return end
        }

        if (buffer.length > maxChars) {
            val limit = minOf(buffer.length, maxChars)
            for (i in limit - 1 downTo 1) {
                if (buffer[i].isWhitespace()) return i + 1
            }
        }
        return -1
    }

    private companion object {
        val SENTENCE_END = charArrayOf('.', '!', '?', '…', '।', '॥', '。', '！', '？', '\n')
        val TRAILING = charArrayOf('"', '\'', ')', ']', '}', '”', '’', '»')
    }
}