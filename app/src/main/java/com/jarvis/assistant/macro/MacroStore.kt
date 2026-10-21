package com.jarvis.assistant.macro

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Persists the user's macros and the recent activity feed. */
object MacroStore {
    private const val PREFS = "macro_center"
    private const val KEY_MACROS = "macros"
    private const val KEY_HISTORY = "history"
    private const val MAX_HISTORY = 30

    fun list(context: Context): List<Macro> {
        val raw = prefs(context).getString(KEY_MACROS, null) ?: return emptyList()
        val arr = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let { MacroCodec.fromJson(it) } }
    }

    /** Insert or update (same id = update). */
    fun save(context: Context, macro: Macro) =
        persist(context, list(context).filterNot { it.id == macro.id } + macro)

    fun delete(context: Context, id: String) =
        persist(context, list(context).filterNot { it.id == id })

    fun updateResult(context: Context, id: String, status: String) =
        persist(context, list(context).map {
            if (it.id == id) it.copy(lastFiredAt = System.currentTimeMillis(), lastStatus = status) else it
        })

    fun addEvent(context: Context, event: MacroEvent) {
        val all = mutableListOf(event)
        all.addAll(history(context))
        val trimmed = JSONArray()
        for (i in 0 until minOf(all.size, MAX_HISTORY)) {
            val e = all[i]
            trimmed.put(JSONObject()
                .put("macro", e.macroName)
                .put("at", e.at)
                .put("status", e.status)
                .put("detail", e.detail))
        }
        prefs(context).edit().putString(KEY_HISTORY, trimmed.toString()).apply()
    }

    fun history(context: Context): List<MacroEvent> {
        val raw = prefs(context).getString(KEY_HISTORY, null) ?: return emptyList()
        val arr = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            MacroEvent(o.optString("macro"), o.optLong("at", 0), o.optString("status"), o.optString("detail"))
        }
    }

    private fun persist(context: Context, macros: List<Macro>) {
        val arr = JSONArray()
        macros.sortedBy { it.createdAt }.forEach { arr.put(MacroCodec.toJson(it)) }
        prefs(context).edit().putString(KEY_MACROS, arr.toString()).apply()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
