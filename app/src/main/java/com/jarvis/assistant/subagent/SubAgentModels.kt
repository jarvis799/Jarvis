package com.jarvis.assistant.subagent

/**
 * DEV STYLE — Sub Agent model layer (pure Kotlin, unit-testable).
 * Every agent listed here is backed by a real, already implemented Jarvis subsystem.
 * Nothing is advertised that the app cannot actually open or run.
 */
enum class SubAgentCategory(val label: String) {
    PROFESSIONAL("Professional"),
    PREMIUM("Premium")
}

/** What a card actually opens when tapped. */
enum class SubAgentTarget {
    WHATSAPP_AUTO_REPLY,
    EMAIL_AUTO_REPLY,
    BROWSER_AGENT,
    TERMINAL,
    FILE_ORGANIZER,
    SCREEN_CONTEXT,
    DEV_STUDIO,
    APP_CONTROL,
    RESEARCH_LAB
}

enum class SubAgentIcon {
    WEB, CODE, CART, PHONE, FOLDER, SEARCH, BUILD, PYTHON, CHAT, MAIL, SCREEN
}

data class SubAgent(
    val id: String,
    val name: String,
    val description: String,
    val icon: SubAgentIcon,
    val category: SubAgentCategory,
    val target: SubAgentTarget,
    /** true = runs by itself (has a real on/off switch + background service) */
    val autoRuns: Boolean = false
)

object SubAgentCatalog {

    const val WHATSAPP_ID = "whatsapp_agent"
    const val EMAIL_ID = "email_agent"

    val all: List<SubAgent> = listOf(
        SubAgent(
            id = WHATSAPP_ID,
            name = "WhatsApp Agent",
            description = "Auto-replies to WhatsApp messages straight from the notification",
            icon = SubAgentIcon.CHAT,
            category = SubAgentCategory.PROFESSIONAL,
            target = SubAgentTarget.WHATSAPP_AUTO_REPLY,
            autoRuns = true
        ),
        SubAgent(
            id = EMAIL_ID,
            name = "Email Agent",
            description = "Reads new Gmail and sends an HTML template reply for you",
            icon = SubAgentIcon.MAIL,
            category = SubAgentCategory.PROFESSIONAL,
            target = SubAgentTarget.EMAIL_AUTO_REPLY,
            autoRuns = true
        ),
        SubAgent(
            id = "web_agent",
            name = "Web Agent",
            description = "Browse the web, search info, summarize content",
            icon = SubAgentIcon.WEB,
            category = SubAgentCategory.PROFESSIONAL,
            target = SubAgentTarget.BROWSER_AGENT
        ),
        SubAgent(
            id = "code_agent",
            name = "Code Agent",
            description = "Write, debug and run code in the built-in terminal",
            icon = SubAgentIcon.CODE,
            category = SubAgentCategory.PROFESSIONAL,
            target = SubAgentTarget.TERMINAL
        ),
        SubAgent(
            id = "app_agent",
            name = "App Agent",
            description = "Open and control Android apps, fill visible fields",
            icon = SubAgentIcon.PHONE,
            category = SubAgentCategory.PROFESSIONAL,
            target = SubAgentTarget.APP_CONTROL
        ),
        SubAgent(
            id = "file_agent",
            name = "File Agent",
            description = "Manage files, read/write and organize your storage",
            icon = SubAgentIcon.FOLDER,
            category = SubAgentCategory.PROFESSIONAL,
            target = SubAgentTarget.FILE_ORGANIZER
        ),
        SubAgent(
            id = "research_agent",
            name = "Research Agent",
            description = "Deep research, GitHub and web reports",
            icon = SubAgentIcon.SEARCH,
            category = SubAgentCategory.PROFESSIONAL,
            target = SubAgentTarget.RESEARCH_LAB
        ),
        SubAgent(
            id = "screen_agent",
            name = "Screen Agent",
            description = "Understand what is on screen and act on it",
            icon = SubAgentIcon.SCREEN,
            category = SubAgentCategory.PROFESSIONAL,
            target = SubAgentTarget.SCREEN_CONTEXT
        ),
        SubAgent(
            id = "build_agent",
            name = "Build Agent",
            description = "Create websites, apps and project setup",
            icon = SubAgentIcon.BUILD,
            category = SubAgentCategory.PROFESSIONAL,
            target = SubAgentTarget.DEV_STUDIO
        ),
        SubAgent(
            id = "shopping_agent",
            name = "Shopping Agent",
            description = "Find products, compare prices, open the product page",
            icon = SubAgentIcon.CART,
            category = SubAgentCategory.PREMIUM,
            target = SubAgentTarget.BROWSER_AGENT
        ),
        SubAgent(
            id = "python_agent",
            name = "Python Agent",
            description = "Execute Python, analyze data, generate scripts",
            icon = SubAgentIcon.PYTHON,
            category = SubAgentCategory.PREMIUM,
            target = SubAgentTarget.TERMINAL
        ),
        SubAgent(
            id = "automation_agent",
            name = "Automation Agent",
            description = "Menu, macro and notification automation center",
            icon = SubAgentIcon.BUILD,
            category = SubAgentCategory.PREMIUM,
            target = SubAgentTarget.APP_CONTROL
        )
    )

    fun inCategory(category: SubAgentCategory): List<SubAgent> = all.filter { it.category == category }

    fun byId(id: String): SubAgent? = all.firstOrNull { it.id == id }

    /** Agents that own a real background worker and therefore expose a switch. */
    fun autoRunning(): List<SubAgent> = all.filter { it.autoRuns }
}

enum class SubAgentState { RUNNING, READY, SETUP, PAUSED }

data class SubAgentStatus(val state: SubAgentState, val label: String, val detail: String) {
    companion object {
        /**
         * Honest status: auto agents report their real configuration + switch,
         * manual agents are READY because their tool screen exists.
         */
        fun of(agent: SubAgent, autoEnabled: Boolean, configured: Boolean): SubAgentStatus {
            if (!agent.autoRuns) {
                return SubAgentStatus(
                    state = SubAgentState.READY,
                    label = "READY",
                    detail = "Opens an installed Jarvis tool"
                )
            }
            return when {
                !configured -> SubAgentStatus(SubAgentState.SETUP, "SETUP", "Needs one-time setup")
                autoEnabled -> SubAgentStatus(SubAgentState.RUNNING, "ACTIVE", "Working in the background")
                else -> SubAgentStatus(SubAgentState.PAUSED, "PAUSED", "Switch is off")
            }
        }
    }
}