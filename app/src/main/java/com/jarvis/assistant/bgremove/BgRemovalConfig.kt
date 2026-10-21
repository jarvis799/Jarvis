package com.jarvis.assistant.bgremove

import android.content.Context

/** Host URL and last known setup facts. Own storage - nothing else in Jarvis is touched. */
class BgRemovalConfig(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var hostUrl: String
        get() = prefs.getString(KEY_HOST, "").orEmpty()
        set(value) { prefs.edit().putString(KEY_HOST, value.trim()).apply() }

    var lastPlanNote: String
        get() = prefs.getString(KEY_PLAN, "").orEmpty()
        set(value) { prefs.edit().putString(KEY_PLAN, value).apply() }

    companion object {
        private const val PREFS = "jarvis_bg_removal"
        private const val KEY_HOST = "host_url"
        private const val KEY_PLAN = "last_plan_note"
        const val DEFAULT_LOCAL_HOST = "http://127.0.0.1:7000"
    }
}
