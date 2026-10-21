package com.jarvis.assistant.devstudio

import android.content.Context
import com.jarvis.assistant.JarvisApp
import org.json.JSONArray

/** Restart-safe job history for the AI Developer Studio (no credentials stored here). */
class ProjectStore(context: Context = JarvisApp.instance) {

    companion object {
        private const val FILE = "jarvis_devstudio_state"
        private const val KEY_JOBS = "jobs"
        private const val MAX_JOBS = 30
    }

    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun all(): List<ProjectJob> {
        val raw = prefs.getString(KEY_JOBS, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let { ProjectJob.fromJson(it) } }
        }.getOrDefault(emptyList()).sortedByDescending { it.created }
    }

    fun find(id: String): ProjectJob? = all().firstOrNull { it.id == id }

    fun save(job: ProjectJob) {
        val rest = all().filterNot { it.id == job.id }
        val list = (listOf(job) + rest).take(MAX_JOBS)
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        prefs.edit().putString(KEY_JOBS, arr.toString()).apply()
    }

    fun remove(id: String) {
        val arr = JSONArray()
        all().filterNot { it.id == id }.forEach { arr.put(it.toJson()) }
        prefs.edit().putString(KEY_JOBS, arr.toString()).apply()
    }

    /** A job interrupted by process death — resumed when the app runs again. */
    fun resumable(): ProjectJob? = all().firstOrNull { !it.stage.finished && it.stage != ProjectStage.PLANNING }
}
