package com.jarvis.assistant.automation.python

import org.json.JSONObject

/**
 * DEV STYLE: a Python snippet the user handed to Jarvis, runnable on demand or automatically.
 * This is the "sub-agent" side of the Python engine - the snippet is the agent's brain, the
 * runtime is real Python inside the bundled Alpine Linux.
 */
data class PythonSnippet(
    val id: String,
    val name: String,
    val code: String,
    val dependencies: String = "",
    val trigger: String = "",
    val autoRun: Boolean = false,
    val lastStatus: String = "",
    val lastRunAt: Long = 0L
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("code", code)
        put("dependencies", dependencies)
        put("trigger", trigger)
        put("autoRun", autoRun)
        put("lastStatus", lastStatus)
        put("lastRunAt", lastRunAt)
    }

    companion object {
        fun fromJson(json: JSONObject): PythonSnippet = PythonSnippet(
            id = json.optString("id").ifBlank { java.util.UUID.randomUUID().toString() },
            name = json.optString("name", "Untitled script"),
            code = json.optString("code", ""),
            dependencies = json.optString("dependencies", ""),
            trigger = json.optString("trigger", ""),
            autoRun = json.optBoolean("autoRun", false),
            lastStatus = json.optString("lastStatus", ""),
            lastRunAt = json.optLong("lastRunAt", 0L)
        )
    }
}