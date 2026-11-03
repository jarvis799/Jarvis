package com.jarvis.assistant.agent

/**
 * "Hello Jarvis" never needs a network round trip. A greeting carries no task, so it is
 * answered here in one step - the whole agent pipeline used to make the owner wait minutes
 * for a line that is already known.
 */
object QuickGreetings {

    private val HELLO = Regex(
        "^(hi|hello|hey|hlo|helo|hii|oi|oye|namaste|salam|assalamu alaikum|salamu alaikum)" +
            "[\\s,!.]*(jarvis|jervis|jarbis|jarwis|jarvi)?" +
            "[\\s,!.]*(are you there|you there|there|suno|sunno|kemon acho|kemon achho|kaise ho|how are you)?" +
            "[\\s,!?.]*$",
        RegexOption.IGNORE_CASE
    )

    private val MORNING = Regex(
        "^(good morning|good afternoon|good evening|good night)[\\s,!.]*(jarvis)?[\\s,!?.]*$",
        RegexOption.IGNORE_CASE
    )

    private val HOW_ARE_YOU = Regex(
        "^(jarvis[\\s,!.]*)?(kemon acho|kemon achho|kaise ho|kaise hain|how are you|how are you doing)[\\s,!?.]*$",
        RegexOption.IGNORE_CASE
    )

    fun reply(input: String): String? {
        val text = input.trim()
        if (text.isEmpty() || text.length > 40) return null
        if (HOW_ARE_YOU.matches(text)) return "Bhalo achi Boss, ami ekdom ready. Apnar din kemon jacche?"
        if (MORNING.matches(text)) {
            val lower = text.lowercase()
            return when {
                lower.contains("night") -> "Shubho ratri Boss. Ami standby te achi."
                lower.contains("afternoon") -> "Good afternoon Boss. Boliye, ki korte hobe?"
                lower.contains("evening") -> "Good evening Boss. Ami shunchhi."
                else -> "Good morning Boss. Aj ki diye shuru korbo?"
            }
        }
        if (HELLO.matches(text)) return "Haan Boss, ami shunchhi. Boliye."
        return null
    }
}
