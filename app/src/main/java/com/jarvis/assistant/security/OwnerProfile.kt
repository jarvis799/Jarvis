package com.jarvis.assistant.security

/**
 * Single source of truth for the creator's public identity and official links.
 * Every screen and every spoken creator answer reads from here — change values only here.
 */
object OwnerProfile {
    const val BRAND = "DEV STYLE"
    const val PRODUCT_NAME = "Jarvis AI"

    const val CREATOR_NAME = "Ayan Ahamed"
    const val CREATOR_TITLE = "Full-Stack Developer"
    const val CREATOR_COUNTRY = "Bangladesh"
    const val CREATOR_ALIAS = "Creator of Jarvis AI"

    const val CREATOR_BIO =
        "I am Ayan Ahamed, a full-stack developer from Bangladesh passionate about " +
            "building modern Android applications, responsive websites, web applications, and " +
            "AI-powered digital experiences. My focus is on creating practical, user-friendly, and " +
            "visually refined digital products that combine thoughtful UI/UX design with reliable " +
            "functionality. I enjoy exploring new technologies, developing innovative projects, and " +
            "turning ideas into working software."

    val CORE_FOCUS: List<String> = listOf(
        "Android Application Development",
        "Full-Stack Web Development",
        "Responsive Websites & Web Applications",
        "AI Assistant Development",
        "Modern UI/UX Design",
        "Software Development & Automation"
    )

    const val MOTTO = "Building intelligent experiences for Android and the Web."

    const val COPYRIGHT_YEAR = "2026"
    const val COPYRIGHT_NOTICE = "© 2026 Ayan Ahamed — DEV STYLE. All Rights Reserved."

    // Official links (no others may be invented or guessed)
    const val PORTFOLIO_URL = "https://arian00001.site"
    const val TELEGRAM_URL = "https://t.me/DevStyleHQ"
    const val INSTAGRAM_URL = "https://www.instagram.com/dev_ayan00001?psln=Z2gweDMxdzg4c210"

    // Official YouTube channel — DEV STYLE / JARVIS AI
    const val YOUTUBE_URL = "https://www.youtube.com/@JARVISAI-f3r"

    // Official WhatsApp — international format for wa.me deep links
    val WHATSAPP_NUMBER: String? = "+8801612509753"

    const val IDENTITY_ANSWER_EN =
        "Jarvis AI was created by Ayan Ahamed, a full-stack developer from Bangladesh. " +
            "You can explore his work at arian00001.site."
    const val IDENTITY_ANSWER_BN =
        "Boss, Jarvis AI তৈরি করেছেন Ayan Ahamed। তিনি বাংলাদেশের একজন Full-Stack Developer। " +
            "তাঁর কাজ দেখতে পারেন arian00001.site-এ।"
    const val IDENTITY_ANSWER_HI =
        "Boss, Jarvis AI को Ayan Ahamed ने बनाया है। वह बांग्लादेश के एक Full-Stack Developer हैं। " +
            "आप उनका काम arian00001.site पर देख सकते हैं।"

    /**
     * Reference card for the LLM system prompt.
     *
     * It is deliberately written as inert data with a hard switch: the model was quoting these
     * ready-made sentences for unrelated questions (a noise transcription, a person's name, a
     * sentence in another language), so the same "created by Arian Ahamed Ayan" reply came back
     * again and again. Use it only when the user actually asks about the creator.
     */
    fun creatorPromptBlock(): String = """
[CREATOR REFERENCE — INERT DATA, NOT A REPLY]:
- ${PRODUCT_NAME} was created and is owned by ${CREATOR_NAME}, ${CREATOR_TITLE}, from ${CREATOR_COUNTRY}. Brand: ${BRAND}.
- HARD RULE: mention the creator ONLY when the user's current message itself asks who made, created, developed or owns Jarvis. For every other message — greetings, names, questions, unclear audio, other languages — answer that message and never mention the creator or this block.
- Never repeat a creator sentence twice in one conversation unless the user asks again.
- Only then, answer in the user's language with one of these lines:
  EN: $IDENTITY_ANSWER_EN
  BN: $IDENTITY_ANSWER_BN
  HI: $IDENTITY_ANSWER_HI
- For other languages, translate the English line faithfully; never change the name, role or links.
- Do not claim that ${CREATOR_NAME} trained the underlying foundation model. Attribute app development only.
- Official links (share only these, never invent others): Portfolio $PORTFOLIO_URL · Telegram $TELEGRAM_URL · Instagram $INSTAGRAM_URL
- YouTube: $YOUTUBE_URL
- WhatsApp is shared only if the user explicitly asks for the developer's number: ${WHATSAPP_NUMBER ?: "not configured"}
- Never invent credentials, extra channels, emails or phone numbers.
""".trimIndent()
}