package com.jarvis.assistant.apkagent

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * DEV STYLE: the skill registry for the APK Agent. Native on-device skills are
 * verified and runnable; external skill libraries are REVIEWED but never
 * executed blindly - their instructions are treated as untrusted input.
 */
object SkillRegistry {

    data class Skill(
        val id: String,
        val name: String,
        val description: String,
        val operations: String,
        val dependencies: String,
        val risk: String,          // LOW | MEDIUM | HIGH
        val status: String,        // NATIVE_VERIFIED | REVIEWED_NOT_INSTALLED | INSTALLED_FLAGGED | BLOCKED
        val source: String,
        val notes: String
    )

    private const val FILE = "skills_registry.json"

    /** Native skills that run fully on-device and are verified against this build. */
    private val nativeSkills = listOf(
        Skill("apk-metadata-analysis", "APK Metadata Analysis", "Reads package, version, SDK levels, permissions and components from a real APK.", "analyze, report", "Android PackageManager (built-in)", "LOW", "NATIVE_VERIFIED", "bundled", "Runs fully on-device."),
        Skill("checksum-verify", "Checksum Verification", "SHA-256 fingerprint of the imported APK - detects any file change.", "hash, verify", "java.security (built-in)", "LOW", "NATIVE_VERIFIED", "bundled", "Runs fully on-device."),
        Skill("zip-structure-inspect", "ZIP Structure Inspection", "Lists DEX files, native ABIs and assets inside the APK archive.", "inspect", "java.util.zip (built-in)", "LOW", "NATIVE_VERIFIED", "bundled", "Runs fully on-device."),
        Skill("skill-import", "Skill Manifest Import", "Imports a skill manifest from a https URL, validates and risk-scans it before listing.", "import, validate", "Network (https only)", "MEDIUM", "NATIVE_VERIFIED", "bundled", "Imported skills are never auto-executed."),
        Skill("project-source-edit", "Project Source Editing", "Applies reviewed changes to an original project repo with checkpoints and review.", "plan, edit, diff", "Original project repo (required)", "MEDIUM", "NATIVE_VERIFIED", "bundled", "Only possible when the original project is connected."),
        Skill("ci-build-verify", "CI Build Verification", "Checks the connected repo's GitHub Actions build status and reports real results.", "build, verify", "GitHub account + repository", "LOW", "NATIVE_VERIFIED", "bundled", "Uses the existing repository connection.")
    )

    /** External skill libraries reviewed for the APK Agent. Truthful dependency notes - nothing fake. */
    private val reviewedExternal = listOf(
        Skill("ext-reverse-skill", "reverse-skill (zhaoxuya520)", "Cybersecurity skills router - Android hooking, CTF and reverse-engineering playbooks (MIT).", "instructions/playbooks only", "EXTERNAL desktop coding agent + Python/Java tooling required - cannot run natively on Android", "HIGH", "REVIEWED_NOT_INSTALLED", "https://github.com/zhaoxuya520/reverse-skill", "Treated as untrusted: scripts are never auto-run; use only on apps you are authorized to test."),
        Skill("ext-agentic-skills", "Agentic Awesome Skills (sickn33)", "Library of 2700+ SKILL.md playbooks for coding agents - code comprehension, testing, docs (MIT).", "instructions/playbooks only", "EXTERNAL desktop coding agent required - cannot run natively on Android", "MEDIUM", "REVIEWED_NOT_INSTALLED", "https://github.com/sickn33/agentic-awesome-skills", "Catalog browsing only; any skill use stays inside the desktop agent, outside this app.")
    )

    private fun registryFile(context: Context): File = File(context.filesDir, "apkagent/skills_registry.json")

    fun list(): List<Skill> {
        val extra = installedExternal()
        return nativeSkills + reviewedExternal + extra
    }

    private fun installedExternal(): List<Skill> {
        val f = registryFile(com.jarvis.assistant.JarvisApp.instance)
        if (!f.exists()) return emptyList()
        return runCatching {
            val arr = JSONObject(f.readText()).optJSONArray("imported") ?: JSONArray()
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                Skill(
                    id = o.optString("id"), name = o.optString("name"),
                    description = o.optString("description"), operations = o.optString("operations"),
                    dependencies = o.optString("dependencies"), risk = o.optString("risk", "MEDIUM"),
                    status = o.optString("status", "INSTALLED"), source = o.optString("source"),
                    notes = o.optString("notes")
                )
            }
        }.getOrDefault(emptyList())
    }

    /** Imports a skill manifest JSON from a https URL. Validates and risk-scans; never executes. */
    suspend fun importFromUrl(context: Context, url: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        if (!url.startsWith("https://")) return@withContext false to "Only https:// skill sources are accepted."
        val body = runCatching {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 10000
            if (conn.responseCode !in 200..299) error("HTTP " + conn.responseCode)
            conn.inputStream.bufferedReader().use { it.readText() }.take(20000)
        }.getOrElse { return@withContext false to "Could not download the skill: " + (it.message ?: "network error") }
        importFromJson(context, body, url)
    }

    fun importFromJson(context: Context, json: String, source: String): Pair<Boolean, String> {
        val obj = runCatching { JSONObject(json.trim()) }.getOrElse { return false to "Invalid skill JSON." }
        val id = obj.optString("id").trim()
        val name = obj.optString("name").trim()
        if (!Regex("^[a-zA-Z0-9_-]{2,40}$").matches(id)) return false to "Skill id must be 2-40 chars (letters, numbers, - _)."
        if (name.isBlank()) return false to "Skill name is required."
        val flags = suspiciousInstructionScan(json)
        val status = if (flags.isEmpty()) "INSTALLED" else "INSTALLED_FLAGGED"
        val f = registryFile(context)
        val root = runCatching { JSONObject(if (f.exists()) f.readText() else "{}") }.getOrElse { JSONObject() }
        val arr = root.optJSONArray("imported") ?: JSONArray()
        val entry = JSONObject()
            .put("id", id).put("name", name)
            .put("description", obj.optString("description"))
            .put("operations", obj.optString("operations"))
            .put("dependencies", obj.optString("dependencies", "unknown - review before use"))
            .put("risk", if (flags.isEmpty()) "MEDIUM" else "HIGH")
            .put("status", status).put("source", source)
            .put("notes", if (flags.isEmpty()) "Validated manifest; execution still requires explicit approval."
                else "Flagged patterns: " + flags.joinToString("; "))
        arr.put(entry)
        root.put("imported", arr)
        runCatching { f.parentFile?.mkdirs(); f.writeText(root.toString(2)) }
        return true to (if (flags.isEmpty()) "Skill imported and validated: " + name
            else "Skill imported but FLAGGED - review before any use: " + flags.joinToString("; "))
    }

    /** Untrusted-input scan: flags dangerous instructions inside imported skill text. */
    fun suspiciousInstructionScan(text: String): List<String> {
        val flags = mutableListOf<String>()
        val lower = text.lowercase()
        val patterns = listOf(
            "rm -rf", "curl" to null, "wget" to null
        )
        if (lower.contains("rm -rf")) flags.add("destructive file deletion command")
        if (Regex("curl[^\\n]*\\|\\s*(sh|bash)").containsMatchIn(lower)) flags.add("pipe-to-shell download")
        if (Regex("wget[^\\n]*\\|\\s*(sh|bash)").containsMatchIn(lower)) flags.add("pipe-to-shell download")
        if (lower.contains("exfiltrate") || lower.contains("keylog")) flags.add("data exfiltration wording")
        if (Regex("bypass\\s+(licen|auth|drm)").containsMatchIn(lower)) flags.add("license/auth bypass wording")
        if (lower.contains("disable security")) flags.add("security-disabling wording")
        return flags
    }

    fun recordExecution(context: Context, skillId: String, summary: String) {
        val f = File(context.filesDir, "apkagent/exec_history.json")
        val root = runCatching { JSONObject(if (f.exists()) f.readText() else "{}") }.getOrElse { JSONObject() }
        val arr = root.optJSONArray("history") ?: JSONArray()
        arr.put(JSONObject().put("skill", skillId).put("at", System.currentTimeMillis()).put("summary", summary.take(500)))
        if (arr.length() > 100) arr.remove(0)
        root.put("history", arr)
        runCatching { f.parentFile?.mkdirs(); f.writeText(root.toString(2)) }
    }
}
