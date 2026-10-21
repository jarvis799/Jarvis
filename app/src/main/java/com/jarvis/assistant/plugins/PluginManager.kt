package com.jarvis.assistant.plugins

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean

/**
 * DEV STYLE: the self-evolving core. Plugins are JSON manifests stored in
 * filesDir/plugins - Jarvis hot-reloads them with no app update, so new
 * skills install at any time from the Plugin Lab.
 */
object PluginManager {

    private val initialized = AtomicBoolean(false)
    private val loaded = LinkedHashMap<String, PluginManifest>()

    fun dir(context: Context): File = File(context.filesDir, "plugins").apply { mkdirs() }

    fun init(context: Context) {
        if (initialized.compareAndSet(false, true)) reload(context)
    }

    /** Hot reload: re-reads every plugin manifest from disk. */
    fun reload(context: Context): Int {
        loaded.clear()
        dir(context).listFiles()?.filter { it.name.endsWith(".json") }?.forEach { file ->
            runCatching { PluginCodec.parse(file.readText()) }.getOrNull()?.let { loaded[it.id] = it }
        }
        return loaded.size
    }

    fun installed(): List<PluginManifest> = loaded.values.toList()

    fun isInstalled(id: String): Boolean = loaded.containsKey(id)

    /** Installs from raw JSON text. Returns (success, message). */
    fun install(context: Context, jsonText: String): Pair<Boolean, String> {
        val manifest = runCatching { PluginCodec.parse(jsonText) }
            .getOrElse { return false to "Invalid plugin JSON: " + (it.message ?: it.javaClass.simpleName) }
        validate(manifest)?.let { return false to it }
        try {
            File(dir(context), manifest.id + ".json").writeText(PluginCodec.toJson(manifest))
        } catch (e: Exception) {
            return false to "Could not save plugin: " + (e.message ?: "storage error")
        }
        loaded[manifest.id] = manifest
        return true to manifest.name + " v" + manifest.version + " installed - " + manifest.commands.size +
            " command(s) live. Hot-reloaded, no restart needed."
    }

    fun uninstall(context: Context, id: String): Boolean {
        loaded.remove(id)
        return File(dir(context), id + ".json").delete()
    }

    fun validate(m: PluginManifest): String? {
        if (!Regex("^[a-zA-Z0-9_-]{2,32}$").matches(m.id)) return "Plugin id must be 2-32 chars: letters, numbers, - or _."
        if (m.name.isBlank()) return "Plugin name is required."
        if (m.version !in 1..999) return "Plugin version must be 1-999."
        if (m.commands.isEmpty()) return "A plugin needs at least one command."
        for (cmd in m.commands) {
            if (cmd.pattern.isBlank()) return "Command pattern cannot be empty."
            if (cmd.isRegex) {
                runCatching { Regex(cmd.pattern, RegexOption.IGNORE_CASE) }
                    .getOrElse { return "Invalid regex '" + cmd.pattern + "': " + (it.message ?: "bad pattern") }
            }
            if (cmd.reply.isBlank() && cmd.action.type in setOf("", "speak")) {
                return "Command '" + cmd.pattern + "' needs a reply or a real action."
            }
            if (cmd.action.type in setOf("call_api", "open_url") && !cmd.action.value.startsWith("https://")) {
                return "Action '" + cmd.action.type + "' must use an https:// URL."
            }
            if (cmd.action.type !in setOf("", "speak", "random", "pick", "call_api", "open_url", "open_app", "terminal")) {
                return "Unknown action type '" + cmd.action.type + "'."
            }
        }
        return null
    }

    /** Returns Jarvis's reply when a plugin command matches the request, or null. */
    suspend fun match(context: Context, request: String): String? {
        init(context)
        val clean = request.trim()
        if (clean.isEmpty()) return null
        for (plugin in loaded.values.toList()) {
            for (cmd in plugin.commands) {
                val groups = matchedGroups(cmd, clean) ?: continue
                val actionResult = withContext(Dispatchers.IO) {
                    runCatching { executeAction(context, cmd, groups, clean) }.getOrElse { e ->
                        "Action failed: " + (e.message ?: e.javaClass.simpleName)
                    }
                }
                val rendered = if (cmd.reply.isBlank()) "" else PluginCodec.render(cmd.reply, groups, clean)
                return when {
                    rendered.isBlank() -> actionResult.ifBlank { "Done." }
                    actionResult.isBlank() || actionResult == "Done." -> rendered
                    else -> rendered + "\n" + actionResult
                }
            }
        }
        return null
    }

    private fun matchedGroups(cmd: PluginCommand, request: String): List<String>? {
        return if (cmd.isRegex) {
            val m = runCatching { Regex(cmd.pattern, RegexOption.IGNORE_CASE) }.getOrNull()?.find(request) ?: return null
            m.groupValues.toList()
        } else {
            if (request.lowercase().contains(cmd.pattern.lowercase())) emptyList() else null
        }
    }

    private fun executeAction(context: Context, cmd: PluginCommand, groups: List<String>, request: String): String = when (cmd.action.type) {
        "", "speak" -> ""
        "random" -> {
            val parts = cmd.action.value.split("..").map { it.trim().toIntOrNull() }
            val lo = parts.getOrNull(0)
            val hi = parts.getOrNull(1)
            if (lo != null && hi != null && lo <= hi) (lo..hi).random().toString() else "1"
        }
        "pick" -> cmd.action.value.split('|').map { it.trim() }.filter { it.isNotBlank() }.random()
        "call_api" -> {
            val parts = cmd.action.value.split('|')
            val url = PluginCodec.render(parts[0].trim(), groups, request)
            val body = runBlocking { fetchText(url) } ?: error("Could not reach " + url.substringBefore('?').take(60))
            if (parts.size > 1 && parts[1].trim().isNotBlank()) {
                extractField(body, parts[1].trim()).ifBlank { body.take(500) }
            } else body.take(500)
        }
        "open_url" -> {
            val url = PluginCodec.render(cmd.action.value, groups, request)
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            "Opened " + url.substringBefore('?').removePrefix("https://")
        }
        "open_app" -> {
            val pkg = PluginCodec.render(cmd.action.value, groups, request).trim()
            val intent = context.packageManager.getLaunchIntentForPackage(pkg)
                ?: error("App not installed: " + pkg)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            "Opened " + pkg
        }
        "terminal" -> {
            val command = PluginCodec.render(cmd.action.value, groups, request)
            val result = runBlocking { com.jarvis.assistant.terminal.TerminalManager.runSuspend(command) }
            (if (result.exitCode == 0) "OK " else "Failed ") + result.output.take(800)
        }
        else -> ""
    }

    /** GET-only https fetch, size-capped - used by plugins that pull live data. */
    suspend fun fetchText(url: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            if (!url.startsWith("https://")) error("Only https URLs are allowed.")
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 12000
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", "Jarvis-Plugin/1.0")
            val code = conn.responseCode
            if (code !in 200..299) error("HTTP " + code)
            val body = conn.inputStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            conn.disconnect()
            body.take(8192)
        }.getOrNull()
    }

    /** Dot-path JSON field extraction, e.g. "choices.0.message.content". */
    private fun extractField(body: String, path: String): String {
        var current: Any = JSONTokener(body).nextValue()
        for (part in path.split('.').filter { it.isNotBlank() }) {
            current = when (current) {
                is JSONObject -> current.opt(part) ?: return ""
                is JSONArray -> current.opt(part.toIntOrNull() ?: return "") ?: return ""
                else -> return current.toString()
            }
        }
        return current.toString().trim()
    }
}
