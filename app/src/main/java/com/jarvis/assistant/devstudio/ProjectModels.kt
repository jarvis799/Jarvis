package com.jarvis.assistant.devstudio

import org.json.JSONArray
import org.json.JSONObject

enum class ProjectStage {
    PLANNING, GENERATING, VALIDATING, PACKAGING, COMPLETED, FAILED, CANCELLED;

    val finished: Boolean get() = this == COMPLETED || this == FAILED || this == CANCELLED

    fun label(): String = when (this) {
        PLANNING -> "Planning"
        GENERATING -> "Generating"
        VALIDATING -> "Validating"
        PACKAGING -> "Packaging"
        COMPLETED -> "Completed"
        FAILED -> "Failed"
        CANCELLED -> "Cancelled"
    }
}

data class ProjectFile(val path: String, val content: String)

data class ProjectPlan(
    val name: String = "",
    val summary: String = "",
    val stack: String = "",
    val pages: List<String> = emptyList(),
    val style: String = "",
    val integrations: List<String> = emptyList(),
    val structure: String = "",
    val stages: List<String> = emptyList(),
    val publishChoice: String = "local"
) {
    fun render(): String {
        val sb = StringBuilder()
        sb.appendLine("Project: " + name)
        sb.appendLine("Summary: " + summary)
        sb.appendLine("Stack: " + stack)
        if (pages.isNotEmpty()) sb.appendLine("Pages: " + pages.joinToString(", "))
        if (style.isNotBlank()) sb.appendLine("Design: " + style)
        if (integrations.isNotEmpty()) sb.appendLine("Integrations: " + integrations.joinToString(", "))
        if (structure.isNotBlank()) sb.appendLine("Structure: " + structure)
        if (stages.isNotEmpty()) sb.appendLine("Stages: " + stages.joinToString(" | "))
        sb.appendLine("Delivery: " + publishChoice)
        return sb.toString().trim()
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("name", name); put("summary", summary); put("stack", stack)
        put("pages", JSONArray(pages)); put("style", style)
        put("integrations", JSONArray(integrations)); put("structure", structure)
        put("stages", JSONArray(stages)); put("publishChoice", publishChoice)
    }

    companion object {
        private fun list(o: JSONObject, key: String): List<String> {
            val arr = o.optJSONArray(key) ?: return emptyList()
            return (0 until arr.length()).map { arr.optString(it) }.filter { it.isNotBlank() }
        }

        fun fromJson(o: JSONObject): ProjectPlan = ProjectPlan(
            name = o.optString("name"),
            summary = o.optString("summary"),
            stack = o.optString("stack"),
            pages = list(o, "pages"),
            style = o.optString("style"),
            integrations = list(o, "integrations"),
            structure = o.optString("structure"),
            stages = list(o, "stages"),
            publishChoice = o.optString("publishChoice", "local")
        )
    }
}

data class ProjectJob(
    val id: String,
    val prompt: String,
    val projectName: String,
    val plan: ProjectPlan? = null,
    val stage: ProjectStage = ProjectStage.PLANNING,
    val progress: Int = 0,
    val message: String = "",
    val filePaths: List<String> = emptyList(),
    val log: List<String> = emptyList(),
    val validation: String = "",
    val zipPath: String = "",
    val zipBytes: Long = 0L,
    val repoUrl: String = "",
    val deployUrl: String = "",
    val error: String = "",
    val created: Long = System.currentTimeMillis()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("prompt", prompt); put("projectName", projectName)
        plan?.let { put("plan", it.toJson()) }
        put("stage", stage.name); put("progress", progress); put("message", message)
        put("filePaths", JSONArray(filePaths)); put("log", JSONArray(log))
        put("validation", validation); put("zipPath", zipPath); put("zipBytes", zipBytes)
        put("repoUrl", repoUrl); put("deployUrl", deployUrl); put("error", error); put("created", created)
    }

    companion object {
        private fun list(o: JSONObject, key: String): List<String> {
            val arr = o.optJSONArray(key) ?: return emptyList()
            return (0 until arr.length()).map { arr.optString(it) }.filter { it.isNotBlank() }
        }

        fun fromJson(o: JSONObject): ProjectJob = ProjectJob(
            id = o.optString("id"),
            prompt = o.optString("prompt"),
            projectName = o.optString("projectName"),
            plan = o.optJSONObject("plan")?.let { ProjectPlan.fromJson(it) },
            stage = runCatching { ProjectStage.valueOf(o.optString("stage", "PLANNING")) }.getOrDefault(ProjectStage.PLANNING),
            progress = o.optInt("progress"),
            message = o.optString("message"),
            filePaths = list(o, "filePaths"),
            log = list(o, "log"),
            validation = o.optString("validation"),
            zipPath = o.optString("zipPath"),
            zipBytes = o.optLong("zipBytes"),
            repoUrl = o.optString("repoUrl"),
            deployUrl = o.optString("deployUrl"),
            error = o.optString("error"),
            created = o.optLong("created", System.currentTimeMillis())
        )
    }
}
