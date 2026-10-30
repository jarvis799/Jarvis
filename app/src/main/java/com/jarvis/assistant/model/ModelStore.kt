package com.jarvis.assistant.model

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Installed local models and the active choice, stored on this device only. */
class ModelStore(context: Context) {

    private companion object {
        const val FILE = "jarvis_local_models"
        const val KEY_MODELS = "models"
        const val KEY_ACTIVE = "active_id"
        const val KEY_OFFLINE_FALLBACK = "offline_fallback"
    }

    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun models(): List<LocalModel> {
        val raw = prefs.getString(KEY_MODELS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                LocalModel(
                    id = item.optString("id"),
                    name = item.optString("name"),
                    filePath = item.optString("filePath"),
                    sizeBytes = item.optLong("sizeBytes"),
                    downloadedAt = item.optLong("downloadedAt"),
                    quant = item.optString("quant")
                )
            }
        }.getOrDefault(emptyList())
    }

    fun save(list: List<LocalModel>) {
        val array = JSONArray()
        list.forEach { model ->
            array.put(JSONObject().apply {
                put("id", model.id)
                put("name", model.name)
                put("filePath", model.filePath)
                put("sizeBytes", model.sizeBytes)
                put("downloadedAt", model.downloadedAt)
                put("quant", model.quant)
            })
        }
        prefs.edit().putString(KEY_MODELS, array.toString()).apply()
    }

    fun add(model: LocalModel) {
        val list = models().filterNot { it.id == model.id } + model
        save(list)
    }

    fun remove(id: String) {
        val wasActive = activeModelId() == id
        save(models().filterNot { it.id == id })
        if (wasActive) setActive(null)
    }

    fun activeModelId(): String? = prefs.getString(KEY_ACTIVE, null)

    fun setActive(id: String?) {
        prefs.edit().putString(KEY_ACTIVE, id).apply()
    }

    fun activeModel(): LocalModel? {
        val id = activeModelId() ?: return null
        return models().firstOrNull { it.id == id }
    }

    /** When true, Jarvis falls back to the installed model if the API is unreachable. */
    fun offlineFallback(): Boolean = prefs.getBoolean(KEY_OFFLINE_FALLBACK, true)

    fun setOfflineFallback(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_OFFLINE_FALLBACK, enabled).apply()
    }
}