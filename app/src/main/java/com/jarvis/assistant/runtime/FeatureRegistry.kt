package com.jarvis.assistant.runtime

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Persists the installed runtime features in filesDir/runtime_features/registry.json. */
object FeatureRegistry {

    fun root(context: Context): File = File(context.filesDir, "runtime_features").apply { mkdirs() }
    fun featureDir(context: Context, id: String): File = File(root(context), id).apply { mkdirs() }

    private fun file(context: Context) = File(root(context), "registry.json")

    fun all(context: Context): List<InstalledFeature> {
        val f = file(context)
        if (!f.exists()) return emptyList()
        val arr = runCatching { JSONArray(f.readText()) }.getOrNull() ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            InstalledFeature(
                id = o.optString("id"),
                name = o.optString("name"),
                version = o.optString("version"),
                featureType = o.optString("feature_type"),
                description = o.optString("description"),
                entry = o.optString("entry"),
                enabled = o.optBoolean("enabled", true),
                installedAt = o.optLong("installed_at", 0L),
                previousVersion = o.optString("previous_version").ifBlank { null }
            )
        }
    }

    fun get(context: Context, id: String): InstalledFeature? = all(context).find { it.id == id }

    fun upsert(context: Context, feature: InstalledFeature) {
        val list = all(context).filter { it.id != feature.id }.toMutableList()
        list += feature
        save(context, list)
    }

    fun setEnabled(context: Context, id: String, enabled: Boolean) {
        save(context, all(context).map { if (it.id == id) it.copy(enabled = enabled) else it })
    }

    fun remove(context: Context, id: String) {
        save(context, all(context).filter { it.id != id })
    }

    private fun save(context: Context, list: List<InstalledFeature>) {
        val arr = JSONArray()
        for (f in list) arr.put(JSONObject()
            .put("id", f.id).put("name", f.name).put("version", f.version)
            .put("feature_type", f.featureType).put("description", f.description)
            .put("entry", f.entry).put("enabled", f.enabled)
            .put("installed_at", f.installedAt)
            .put("previous_version", f.previousVersion ?: ""))
        file(context).writeText(arr.toString())
    }
}
