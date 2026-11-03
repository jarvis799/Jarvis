package com.jarvis.assistant.voice

/**
 * Turns a written reply into something that sounds right when spoken.
 *
 * Markdown, code fences, bare links, list markers and emoji are written for the eye; spoken
 * aloud they become "asterisk asterisk" and URL soup. Everything else - including Bengali and
 * Hindi combining marks - is left exactly as written.
 */
object SpeechText {

    private val FENCED_CODE = Regex("\u0060\u0060\u0060[\\s\\S]*?\u0060\u0060\u0060")
    private val INLINE_CODE = Regex("\u0060([^\u0060]*)\u0060")
    private val MD_LINK = Regex("\\[([^\\]]*)\\]\\([^)]*\\)")
    private val BARE_URL = Regex("https?://\\S+")
    private val HEADING = Regex("^#{1,6}\\s*", RegexOption.MULTILINE)
    private val BULLET = Regex("^\\s*[-*\u2022\u2013\u2014]\\s+", RegexOption.MULTILINE)
    private val NUMBERED = Regex("^\\s*\\d+[.)]\\s+", RegexOption.MULTILINE)
    private val EMPHASIS = Regex("[*_~\u0060]+")
    private val SPACES = Regex("[ \\t]+")

    fun clean(raw: String): String {
        if (raw.isBlank()) return ""
        var value = raw
        value = FENCED_CODE.replace(value, " ")
        value = INLINE_CODE.replace(value, "$1")
        value = MD_LINK.replace(value, "$1")
        value = BARE_URL.replace(value, " ")
        value = HEADING.replace(value, "")
        value = BULLET.replace(value, "")
        value = NUMBERED.replace(value, "")
        value = EMPHASIS.replace(value, "")
        value = stripEmoji(value)
        value = SPACES.replace(value, " ")
        return value.lines().joinToString(" ") { it.trim() }.trim()
    }

    private fun stripEmoji(value: String): String {
        val out = StringBuilder(value.length)
        var index = 0
        while (index < value.length) {
            val codePoint = value.codePointAt(index)
            if (!isEmoji(codePoint)) out.appendCodePoint(codePoint)
            index += Character.charCount(codePoint)
        }
        return out.toString()
    }

    private fun isEmoji(codePoint: Int): Boolean = when (codePoint) {
        in 0x1F000..0x1FAFF, in 0x2600..0x27BF, in 0x2B00..0x2BFF,
        in 0x1F1E6..0x1F1FF, in 0x2190..0x21FF, 0xFE0F, 0x200D -> true
        else -> false
    }
}
