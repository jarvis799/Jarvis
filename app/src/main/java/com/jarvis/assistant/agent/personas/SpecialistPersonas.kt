package com.jarvis.assistant.agent.personas

/**
 * Specialist AI personas. Jarvis adopts the most relevant persona based on the user's request.
 *
 * DEV STYLE: the roster is extended with specialists adapted from the open-source
 * agency-agents agent library (github.com/msitarzewski/agency-agents) so complex
 * developer requests get decomposed to a real specialist instead of a general chat.
 */
object SpecialistPersonas {
    enum class Persona(val displayName: String, val systemBlock: String, val triggers: List<String>) {
        CODER("Coder", "Adopt a careful software engineer persona. Give precise, tested, secure code. ", listOf("code", "function", "bug", "compile", "কোড", "প্রোগ্রাম", "debug", "api")),
        RESEARCHER("Researcher", "Adopt a thorough researcher persona. Cite reasoning and distinguish fact from inference. ", listOf("research", "study", "analyze", "compare", "গবেষণা", "তুলনা")),
        THERAPIST("Therapist", "Adopt a warm, empathetic listener persona. Be supportive and non-judgmental. Never give medical diagnoses. ", listOf("stress", "anxiety", "feel", "worried", "মানসিক", "উদ্বিগ্ন", "কষ্ট")),
        TUTOR("Tutor", "Adopt a patient tutor persona. Explain step by step with examples. ", listOf("learn", "teach", "explain", "understand", "শেখা", "বুঝতে")),
        CREATIVE("Creative", "Adopt a creative writer persona. Be imaginative and original. ", listOf("write", "story", "poem", "creative", "গল্প", "কবিতা", "লেখা")),
        // DEV STYLE: agency-agents roster (github.com/msitarzewski/agency-agents)
        FRONTEND_DEVELOPER("Frontend Developer", "Adopt the agency-agents Frontend Developer persona. Build clean, responsive, accessible interfaces with minimal, tested markup and styles. ", listOf("frontend", "responsive", "react", "css", "html page")),
        BACKEND_ARCHITECT("Backend Architect", "Adopt the agency-agents Backend Architect persona. Design reliable APIs and data models; think about failure modes before happy paths. ", listOf("backend", "api design", "server", "database schema", "endpoint design")),
        CODE_REVIEWER("Code Reviewer", "Adopt the agency-agents Code Reviewer persona. Review for correctness, security and readability; give prioritized, actionable findings. ", listOf("code review", "review this code", "is this code correct")),
        DEVOPS_AUTOMATOR("DevOps Automator", "Adopt the agency-agents DevOps Automator persona. Automate builds, CI and deployment with minimal, robust steps. ", listOf("github actions", "ci pipeline", "deployment pipeline", "automate the build")),
        DATA_ENGINEER("Data Engineer", "Adopt the agency-agents Data Engineer persona. Structure, clean and move data safely; validate before claiming success. ", listOf("dataset", "csv", "etl", "data pipeline")),
        MOBILE_ENGINEER("Mobile Engineer", "Adopt the agency-agents Mobile Engineer persona for Android/Kotlin work. Respect platform limits, battery and permissions. ", listOf("android", "apk", "kotlin", "gradle")),
        UI_DESIGNER("UI Designer", "Adopt the agency-agents UI Designer persona. Clean layouts, clear hierarchy, consistent color and type; suggest concrete values. ", listOf("ui", "mockup", "color scheme")),
        UX_ARCHITECT("UX Architect", "Adopt the agency-agents UX Architect persona. Map user flows and edge cases before proposing screens. ", listOf("ux", "user flow", "wireframe", "navigation structure")),
        BRAND_GUARDIAN("Brand Guardian", "Adopt the agency-agents Brand Guardian persona. Keep tone, naming and visuals consistent with the existing brand. ", listOf("branding", "logo", "brand identity")),
        ONBOARDING_ENGINEER("Codebase Onboarding", "Adopt the agency-agents Codebase Onboarding persona. Explain an unfamiliar codebase as a structured, plain-language tour. ", listOf("onboarding", "how does this project work", "walk me through the project")),
        GENERAL("General", "", emptyList());
    }
    fun select(input: String): Persona {
        val lower = input.lowercase()
        for (persona in Persona.values().filter { it != Persona.GENERAL }) {
            if (persona.triggers.any { lower.contains(it) }) return persona
        }
        return Persona.GENERAL
    }
}
