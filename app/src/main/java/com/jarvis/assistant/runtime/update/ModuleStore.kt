package com.jarvis.assistant.runtime.update

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject

/** Persistent registry of installed runtime modules, the active runtime
 *  version, the retained previous version of each module (for rollback) and
 *  the last discovery timestamp. Backed by EncryptedSharedPreferences so the
 *  module table and rollback pointers are stored at rest. */
object ModuleStore {

    private const val PREFS = "jarvis_runtime_update_store"
    private const val KEY_RUNTIME_VERSION = "runtime_version"
    private const val KEY_MODULES = "modules"
    private const val KEY_LAST_CHECKED = "last_checked"

    data class InstalledModule(
        val id: String, val name: String, val version: String, val type: String,
        val sha256: String, val filePath: String, val active: Boolean
    )

    private fun prefs(ctx: Context) = EncryptedSharedPreferences.create(
        ctx, PREFS,
        MasterKey.Builder(ctx).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun activeRuntimeVersion(ctx: Context): String =
        prefs(ctx).getString(KEY_RUNTIME_VERSION, "1.0.0") ?: "1.0.0"

    fun setRuntimeVersion(ctx: Context, version: String) {
        prefs(ctx).edit().putString(KEY_RUNTIME_VERSION, version).apply()
    }

    fun lastChecked(ctx: Context): Long = prefs(ctx).getLong(KEY_LAST_CHECKED, 0L)
    fun setLastChecked(ctx: Context, ms: Long) { prefs(ctx).edit().putLong(KEY_LAST_CHECKED, ms).apply() }

    fun modules(ctx: Context): List<InstalledModule> {
        val arr = prefs(ctx).getString(KEY_MODULES, null) ?: return emptyList()
        val out = mutableListOf<InstalledModule>()
        val a = JSONArray(arr)
        for (i in 0 until a.length()) {
            val o = a.optJSONObject(i) ?: continue
            out += InstalledModule(
                o.optString("id"), o.optString("name"), o.optString("version"),
                o.optString("type"), o.optString("sha256"), o.optString("file"),
                o.optBoolean("active", false)
            )
        }
        return out
    }

    fun previousVersion(ctx: Context, id: String): InstalledModule? =
        modules(ctx).firstOrNull { it.id == id && !it.active }

    fun active(ctx: Context, id: String): InstalledModule? =
        modules(ctx).firstOrNull { it.id == id && it.active }

    fun upsert(ctx: Context, module: InstalledModule) {
        val current = modules(ctx).toMutableList()
        current.removeAll { it.id == module.id && it.version == module.version }
        current += module
        write(ctx, current)
    }

    fun setActive(ctx: Context, id: String, version: String) {
        val current = modules(ctx).map {
            if (it.id == id) it.copy(active = (it.version == version)) else it
        }
        write(ctx, current)
    }

    fun remove(ctx: Context, id: String, version: String) {
        write(ctx, modules(ctx).filterNot { it.id == id && it.version == version })
    }

    private fun write(ctx: Context, list: List<InstalledModule>) {
        val a = JSONArray()
        for (m in list) {
            a.put(JSONObject().apply {
                put("id", m.id); put("name", m.name); put("version", m.version)
                put("type", m.type); put("sha256", m.sha256); put("file", m.filePath)
                put("active", m.active)
            })
        }
        prefs(ctx).edit().putString(KEY_MODULES, a.toString()).apply()
    }
}
