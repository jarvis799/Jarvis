package com.jarvis.assistant.security

/**
 * Single source of truth for the creator's public identity and official links.
 * Every screen and every spoken creator answer reads from here — change values only here.
 */
object OwnerProfile {
    const val BRAND = "DEV STYLE"
    const val PRODUCT_NAME = "Jarvis AI"

    const val CREATOR_NAME = "Arian Ahamed Ayan"
    const val CREATOR_TITLE = "Junior Full-Stack Developer | Android & Web Application Developer"
    const val CREATOR_COUNTRY = "Bangladesh"
    const val CREATOR_ALIAS = "Creator of Jarvis AI"

    const val CREATOR_BIO =
        "I am Arian Ahamed Ayan, a junior full-stack developer from Bangladesh passionate about " +
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
    const val COPYRIGHT_NOTICE = "© 2026 Arian Ahamed Ayan — DEV STYLE. All Rights Reserved."

    // Official links (no others may be invented or guessed)
    const val PORTFOLIO_URL = "https://arian00001.site"
    const val TELEGRAM_URL = "https://t.me/DevStyleHQ"
    const val INSTAGRAM_URL = "https://www.instagram.com/dev_ayan00001"

    // Official YouTube channel — DEV STYLE / JARVIS AI
    const val YOUTUBE_URL = "https://www.youtube.com/@JARVISAI-f3r"

    // Official WhatsApp — international format for wa.me deep links
    val WHATSAPP_NUMBER: String? = "+8801612509753"

    const val IDENTITY_ANSWER_EN =
        "Jarvis AI was created by Arian Ahamed Ayan, a junior full-stack developer from Bangladesh. " +
            "You can explore his work at arian00001.site."
    const val IDENTITY_ANSWER_BN =
        "Boss, Jarvis AI তৈরি করেছেন Arian Ahamed Ayan। তিনি বাংলাদেশের একজন Junior Full-Stack Developer। " +
            "তাঁর কাজ দেখতে পারেন arian00001.site-এ।"
    const val IDENTITY_ANSWER_HI =
        "Boss, Jarvis AI को Arian Ahamed Ayan ने बनाया है। वह बांग्लादेश के एक Junior Full-Stack Developer हैं। " +
            "आप उनका काम arian00001.site पर देख सकते हैं।"

    /** Compact creator block injected into the LLM system prompt. */
    fun creatorPromptBlock(): String = """
[CREATOR IDENTITY & ATTRIBUTION]:
- ${PRODUCT_NAME} was created and is owned by ${CREATOR_NAME}, ${CREATOR_TITLE}, from ${CREATOR_COUNTRY}. Brand: ${BRAND}.
- If asked who created / developed / owns you, answer in the user's language and use these exact lines:
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