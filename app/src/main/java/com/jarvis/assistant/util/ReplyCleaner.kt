package com.jarvis.assistant.util

/**
 * DEV STYLE: keeps Jarvis from repeating itself.
 *
 * Models (online and offline) sometimes answer the same sentence twice in one reply, or
 * open with a "Thinking..." line. Neither is useful, both feel slow. This collapses an
 * exact repeated sentence once, keeping the first copy in its original position, and
 * strips leading meta lines. Nothing else about the answer is rewritten.
 */
object ReplyCleaner {

    private val META_PREFIX = Regex(
        "^(thinking|thought|soch raha hoon|sochti hoon|सोच रहा हूँ|सोच रही हूँ|thinking\\.\\.\\.)[:.\\s]*",
        RegexOption.IGNORE_CASE
    )
    private val SENTENCE_SPLIT = Regex("(?<=[.!?।\\n])\\s+")

    fun clean(reply: String): String {
        var text = reply.trim()
        if (text.isEmpty()) return text
        text = META_PREFIX.replace(text, "").trim()
        if (text.isEmpty()) return reply.trim()

        val kept = ArrayList<String>()
        val seen = HashSet<String>()
        for (part in text.split(SENTENCE_SPLIT)) {
            val key = part.lowercase()
                .replace(Regex("[^a-z0-9\\u0900-\\u097F ]"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
            if (key.isEmpty()) continue
            if (!seen.add(key)) continue
            kept.add(part.trim())
        }
        if (kept.isEmpty()) return text
        val joined = kept.joinToString(" ").replace(Regex("\\s+([.!?,])"), "\$1").trim()
        return joined.ifBlank { text }
    }
}