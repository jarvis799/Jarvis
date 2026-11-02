package com.jarvis.assistant.automation.python

import android.content.Context
import com.jarvis.assistant.JarvisApp
import org.json.JSONArray
import java.util.UUID

/** DEV STYLE: persistent store for the user's own Python snippets. */
object PythonSnippetStore {

    private const val PREFS = "jarvis_python_snippets"
    private const val KEY = "snippets"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun all(context: Context = JarvisApp.instance): List<PythonSnippet> {
        val raw = prefs(context).getString(KEY, "") ?: ""
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                array.optJSONObject(index)?.let { PythonSnippet.fromJson(it) }
            }
        }.getOrDefault(emptyList())
    }

    fun enabledAutoRun(context: Context = JarvisApp.instance): List<PythonSnippet> =
        all(context).filter { it.autoRun && it.code.isNotBlank() }

    fun findByName(context: Context, name: String): PythonSnippet? {
        val needle = name.trim().lowercase()
        if (needle.isBlank()) return null
        val snippets = all(context)
        return snippets.firstOrNull { it.name.lowercase() == needle }
            ?: snippets.firstOrNull { it.name.lowercase().contains(needle) }
    }

    fun findById(context: Context, id: String): PythonSnippet? = all(context).firstOrNull { it.id == id }

    fun save(context: Context, snippet: PythonSnippet): PythonSnippet {
        val target = if (snippet.id.isBlank()) snippet.copy(id = UUID.randomUUID().toString()) else snippet
        val existing = all(context).toMutableList()
        val index = existing.indexOfFirst { it.id == target.id }
        if (index >= 0) existing[index] = target else existing.add(target)
        persist(context, existing)
        PythonSnippetScheduler.sync(context)
        return target
    }

    fun delete(context: Context, id: String) {
        persist(context, all(context).filterNot { it.id == id })
        PythonSnippetScheduler.sync(context)
    }

    fun recordRun(context: Context, id: String, status: String) {
        val existing = all(context).toMutableList()
        val index = existing.indexOfFirst { it.id == id }
        if (index < 0) return
        existing[index] = existing[index].copy(lastStatus = status, lastRunAt = System.currentTimeMillis())
        persist(context, existing)
    }

    private fun persist(context: Context, snippets: List<PythonSnippet>) {
        val array = JSONArray()
        snippets.forEach { array.put(it.toJson()) }
        prefs(context).edit().putString(KEY, array.toString()).apply()
    }
}