package com.jarvis.assistant.plugins

import org.json.JSONArray
import org.json.JSONObject

/** A plugin is a small JSON manifest that teaches Jarvis new commands - no app update needed. */
data class PluginManifest(
    val id: String,
    val name: String,
    val version: Int,
    val description: String,
    val author: String,
    val commands: List<PluginCommand>
)

data class PluginCommand(
    val pattern: String,
    val isRegex: Boolean,
    val reply: String,
    val action: PluginAction
)

data class PluginAction(val type: String, val value: String)

/** Parses, validates and renders plugin manifests. */
object PluginCodec {

    fun parse(jsonText: String): PluginManifest {
        val json = JSONObject(jsonText.trim())
        val commands = mutableListOf<PluginCommand>()
        val arr = json.optJSONArray("commands") ?: JSONArray()
        for (i in 0 until arr.length()) {
            val c = arr.optJSONObject(i) ?: continue
            val a = c.optJSONObject("action")
            commands += PluginCommand(
                pattern = c.optString("pattern").trim(),
                isRegex = c.optBoolean("isRegex", false),
                reply = c.optString("reply").trim(),
                action = PluginAction(
                    type = a?.optString("type").orEmpty().trim().lowercase(),
                    value = a?.optString("value").orEmpty().trim()
                )
            )
        }
        return PluginManifest(
            id = json.optString("id").trim(),
            name = json.optString("name").trim(),
            version = json.optInt("version", 1),
            description = json.optString("description").trim(),
            author = json.optString("author").trim(),
            commands = commands
        )
    }

    fun toJson(m: PluginManifest): String {
        val json = JSONObject()
            .put("id", m.id).put("name", m.name).put("version", m.version)
            .put("description", m.description).put("author", m.author)
        val arr = JSONArray()
        for (c in m.commands) {
            val cmd = JSONObject().put("pattern", c.pattern).put("isRegex", c.isRegex).put("reply", c.reply)
            cmd.put("action", JSONObject().put("type", c.action.type).put("value", c.action.value))
            arr.put(cmd)
        }
        json.put("commands", arr)
        return json.toString(2)
    }

    /** Fills the placeholder markers: group refs and the full request. */
    fun render(template: String, groups: List<String>, request: String): String {
        var out = template.replace("{input}", request)
        groups.forEachIndexed { i, g -> out = out.replace("{" + i + "}", g) }
        return out
    }
}
