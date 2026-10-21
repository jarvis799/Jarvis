package com.jarvis.assistant.devstudio

import org.json.JSONArray
import org.json.JSONObject

/**
 * Planning and code generation prompts. Technology choices requested by the user are
 * never silently swapped, and credentials are never part of a prompt.
 */
object AiProjectBrain {

    private const val VOICE = "You are Jarvis, the user's own coding assistant inside their Android app. " +
        "The user speaks English, Bangla or Banglish - answer in the same language they used, short and practical. " +
        "Never invent credentials, API keys or tokens. Never ask for a password."

    fun isSimpleRequest(prompt: String): Boolean {
        val t = prompt.lowercase()
        if (t.split(Regex("\\s+")).size <= 2) return false
        val simpleWords = listOf("simple", "single page", "ekta page", "landing", "small", "quick")
        return simpleWords.any { t.contains(it) } || t.trim().length < 45
    }

    val questions = listOf(
        "Ki banao chanp - ek line e bolun.",
        "Ei site/app ta kar jonno, uddesho ki?",
        "Kon technology chan (React, HTML, Three.js, Android)?",
        "Kon kon page/feature lagbe?",
        "Look, color ar animation kemon hobe?",
        "Kono API/integration lagbe?",
        "Local file e rakhbo, GitHub e push korbo, naki Vercel e deploy korbo?"
    )

    suspend fun nextQuestion(prompt: String, answers: List<String>): String {
        val asked = answers.indices.joinToString(", ") { questions.getOrElse(it) { "" } }
        val reply = DevStudioAi.askWithRetry(
            VOICE + "\nThe user wants to build: " + prompt + "\nThey already answered these questions: " + asked +
                " (answers: " + answers.joinToString(" | ") + ").\nAsk exactly ONE short next question that is still missing" +
                " (purpose, technology, pages, style, integrations or delivery choice). Reply with the question only."
        )
        return reply.trim().lines().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
    }

    suspend fun buildPlan(prompt: String, answers: List<String>): ProjectPlan {
        val ask = VOICE + "\nBuild request: " + prompt + "\nUser answers: " + answers.joinToString(" | ") +
            "\nReturn ONLY JSON with keys: name (short project slug), summary, stack, pages (array of page names)," +
            " style, integrations (array), structure (folder layout as text), stages (array of implementation steps)," +
            " publishChoice (local | github | vercel)." +
            "\nRules: if the user explicitly asked for HTML, use a standalone HTML/CSS/JS project with no build system." +
            " If they asked for React, use React with Vite and JavaScript. If they asked for Three.js, use Three.js" +
            " (React Three Fiber only for a React project). If they asked for Android, plan a native Android (Kotlin) project." +
            " Otherwise recommend React + Vite and explain briefly in summary."
        val reply = DevStudioAi.askWithRetry(ask)
        val o = runCatching { JSONObject(DevStudioRules.extractJson(reply)) }.getOrElse {
            throw IllegalStateException("The AI reply could not be read as a project plan. Try again.")
        }
        return ProjectPlan.fromJson(o).let { plan ->
            if (plan.name.isBlank() || plan.summary.isBlank()) {
                throw IllegalStateException("The AI plan was incomplete. Try again with one more detail.")
            }
            plan.copy(name = DevStudioRules.sanitizeProjectName(plan.name))
        }
    }

    suspend fun fileList(plan: ProjectPlan): List<String> {
        val reply = DevStudioAi.askWithRetry(
            VOICE + "\nProject plan:\n" + plan.render() +
                "\nReturn ONLY JSON: {\"files\":[\"path\", ...]} listing every source file this project needs." +
                " Include package manifest and config files when the stack needs them. Maximum 40 files," +
                " no node_modules, no build output, no .env or secret files."
        )
        val arr = runCatching { JSONObject(DevStudioRules.extractJson(reply)).optJSONArray("files") }.getOrNull() ?: JSONArray()
        val paths = (0 until arr.length()).map { arr.optString(it) }.filter { it.isNotBlank() }
        return paths.filter { DevStudioRules.isSafeRelativePath(it) && !DevStudioRules.isExcludedPath(it) }.distinct().take(40)
    }

    suspend fun generateBatch(plan: ProjectPlan, paths: List<String>): List<ProjectFile> {
        val reply = DevStudioAi.askWithRetry(
            VOICE + "\nProject: " + plan.name + " (" + plan.stack + ")." + "\nPlan:\n" + plan.render() +
                "\nWrite these files completely: " + paths.joinToString(", ") +
                "\nReturn ONLY JSON: {\"files\":[{\"path\":\"…\",\"content\":\"full file body\"}]}." +
                " Every file must be complete, production quality and internally consistent with the others." +
                " Never include API keys, tokens or placeholders like YOUR_API_KEY inside code - use a config file with empty values."
        )
        val arr = runCatching { JSONObject(DevStudioRules.extractJson(reply)).optJSONArray("files") }.getOrNull()
            ?: throw IllegalStateException("The AI reply could not be read as source files.")
        val files = (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val path = o.optString("path")
            val content = o.optString("content")
            if (path.isBlank() || content.isBlank()) null else ProjectFile(path, content)
        }
        if (files.isEmpty()) throw IllegalStateException("The AI returned no usable file content.")
        return files
    }
}
