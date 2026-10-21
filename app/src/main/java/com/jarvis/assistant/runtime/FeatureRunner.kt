package com.jarvis.assistant.runtime

import android.content.Context
import com.jarvis.assistant.promptlab.PromptLabStore
import com.jarvis.assistant.plugins.PluginManager
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Activates installed features through the plugin interfaces Jarvis already
 * has: command packs hot-reload into PluginManager, prompt packs land in the
 * Prompt Lab, and web tools open sandboxed. No downloaded code is executed.
 */
object FeatureRunner {

    class ActivationException(message: String) : Exception(message)

    fun featureDir(context: Context, feature: InstalledFeature): File =
        File(FeatureRegistry.featureDir(context, feature.id), feature.version)

    /** Activates a feature immediately - no restart needed. */
    fun activate(context: Context, feature: InstalledFeature): String = when (feature.featureType) {
        "commands" -> {
            val pluginFile = File(featureDir(context, feature), "plugin.json")
            if (!pluginFile.isFile) throw ActivationException("plugin.json is missing from the installed package.")
            val (ok, message) = PluginManager.install(context, pluginFile.readText())
            if (!ok) throw ActivationException(message)
            message
        }
        "prompt_pack" -> {
            val prompts = File(featureDir(context, feature), "prompts.json")
            if (!prompts.isFile) throw ActivationException("prompts.json is missing from the installed package.")
            val store = PromptLabStore(context)
            val arr = JSONArray(prompts.readText())
            if (arr.length() == 0) throw ActivationException("The prompt pack is empty.")
            var added = 0
            for (i in 0 until arr.length()) {
                val p = arr.optJSONObject(i) ?: continue
                val name = p.optString("name")
                if (name.isBlank() || p.optString("content").isBlank())
                    throw ActivationException("Every prompt needs a name and content.")
                store.getAll().find { it.name == name }?.let { store.deletePrompt(it.id) }
                store.createPrompt(
                    name, p.optString("content"), tagsOf(p),
                    p.optString("task_type", "Runtime Feature"), p.optString("provider", "Runtime Feature")
                )
                added++
            }
            "Updated the Prompt Lab with " + added + " prompt(s)."
        }
        "web_tool" -> {
            val entry = feature.entry.ifBlank { "index.html" }
            if (!File(featureDir(context, feature), entry).isFile)
                throw ActivationException("The tool's entry page is missing.")
            "Ready - tap Open to launch the tool."
        }
        else -> throw ActivationException("Unsupported feature type '" + feature.featureType + "'.")
    }

    /** Disables a feature: a command pack is removed from the live plugin engine. */
    fun deactivate(context: Context, feature: InstalledFeature): String = when (feature.featureType) {
        "commands" -> { PluginManager.uninstall(context, feature.id); "Commands disabled." }
        else -> "Disabled - the feature stays installed but hidden."
    }

    private fun tagsOf(p: JSONObject): List<String> {
        val arr = p.optJSONArray("tags") ?: return emptyList()
        return (0 until arr.length()).map { arr.optString(it) }
    }
}
