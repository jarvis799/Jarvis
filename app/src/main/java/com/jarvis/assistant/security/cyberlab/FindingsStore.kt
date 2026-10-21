package com.jarvis.assistant.security.cyberlab

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * MODULE 5 — Security Findings Engine.
 * Structured, deduplicated findings with status history (NEW / CONFIRMED /
 * FIXED / RETEST_REQUIRED). Evidence must be redacted before it is stored:
 * this store never holds secrets, tokens or passwords.
 */
class FindingsStore(private val context: Context) {

    data class Finding(
        val id: String,
        val component: String,
        val category: String,
        val title: String,
        val evidence: String,
        val severity: String,
        val confidence: String,
        val impact: String,
        val remediation: String,
        val verification: String,
        val status: String,
        val history: List<String>,
        val firstSeen: Long,
        val lastSeen: Long
    )

    data class RawFinding(
        val component: String,
        val category: String,
        val title: String,
        val evidence: String,
        val severity: String,
        val confidence: String,
        val impact: String,
        val remediation: String,
        val verification: String
    )

    companion object {
        val STATUSES = listOf("NEW", "CONFIRMED", "FIXED", "RETEST_REQUIRED")
        val SEVERITY_ORDER = listOf("CRITICAL", "HIGH", "MEDIUM", "LOW", "INFO")
    }

    private fun file() = File(context.filesDir, "jarvis_cyber_findings.json")

    private fun load(): JSONArray {
        val f = file()
        if (!f.exists()) return JSONArray()
        return try { JSONArray(f.readText()) } catch (e: Exception) { JSONArray() }
    }

    private fun save(arr: JSONArray) = file().writeText(arr.toString())

    private fun keyOf(component: String, category: String, title: String): String =
        (component + "|" + category + "|" + title).lowercase()

    /** Adds a finding, or updates evidence on the existing duplicate (same component+category+title). */
    fun addOrUpdate(raw: RawFinding): Finding {
        val all = all().toMutableList()
        val now = System.currentTimeMillis()
        val k = keyOf(raw.component, raw.category, raw.title)
        val idx = all.indexOfFirst { keyOf(it.component, it.category, it.title) == k }
        val finding = if (idx >= 0) {
            val old = all[idx]
            old.copy(evidence = raw.evidence, lastSeen = now, history = old.history + ("evidence refreshed @" + now))
        } else {
            Finding(
                id = UUID.randomUUID().toString(), component = raw.component, category = raw.category,
                title = raw.title, evidence = raw.evidence, severity = raw.severity,
                confidence = raw.confidence, impact = raw.impact, remediation = raw.remediation,
                verification = raw.verification, status = "NEW", history = listOf("created"),
                firstSeen = now, lastSeen = now
            )
        }
        if (idx >= 0) all[idx] = finding else all.add(finding)
        save(JSONArray(all.map { it.toJson() }))
        return finding
    }

    fun all(): List<Finding> {
        val arr = load()
        return (0 until arr.length()).map { fromJson(arr.getJSONObject(it)) }
    }

    fun updateStatus(id: String, newStatus: String, note: String): Finding? {
        if (newStatus !in STATUSES) return null
        val all = all().toMutableList()
        val idx = all.indexOfFirst { it.id == id }
        if (idx < 0) return null
        val f = all[idx]
        val updated = f.copy(status = newStatus, history = f.history + (newStatus + " (" + note + ") @" + System.currentTimeMillis()))
        all[idx] = updated
        save(JSONArray(all.map { it.toJson() }))
        return updated
    }

    fun delete(id: String) = save(JSONArray(all().filter { it.id != id }.map { it.toJson() }))

    fun clear() { file().delete() }

    /** Prioritized plain-text report (severity first). Never includes raw secrets. */
    fun exportText(): String {
        val sb = StringBuilder("JARVIS APEX - SECURITY FINDINGS REPORT\n\n")
        val sorted = all().sortedBy { f -> SEVERITY_ORDER.indexOf(f.severity).let { if (it < 0) 99 else it } }
        sorted.forEachIndexed { i, f ->
            sb.append("#").append(i + 1).append(" [").append(f.severity).append("/").append(f.confidence).append("] ").append(f.title).append("\n")
            sb.append("  component: ").append(f.component).append("\n")
            sb.append("  category: ").append(f.category).append("\n")
            sb.append("  status: ").append(f.status).append("\n")
            sb.append("  evidence: ").append(f.evidence).append("\n")
            sb.append("  impact: ").append(f.impact).append("\n")
            sb.append("  fix: ").append(f.remediation).append("\n")
            sb.append("  verify: ").append(f.verification).append("\n\n")
        }
        if (sorted.isEmpty()) sb.append("No findings recorded.\n")
        return sb.toString()
    }

    private fun fromJson(o: JSONObject): Finding {
        val h = o.optJSONArray("history")
        return Finding(
            id = o.optString("id"), component = o.optString("component"), category = o.optString("category"),
            title = o.optString("title"), evidence = o.optString("evidence"),
            severity = o.optString("severity", "MEDIUM"), confidence = o.optString("confidence", "THEORETICAL"),
            impact = o.optString("impact"), remediation = o.optString("remediation"),
            verification = o.optString("verification"), status = o.optString("status", "NEW"),
            history = if (h == null) emptyList() else (0 until h.length()).map { h.optString(it) },
            firstSeen = o.optLong("firstSeen"), lastSeen = o.optLong("lastSeen")
        )
    }

    private fun Finding.toJson(): JSONObject {
        val h = JSONArray()
        history.forEach { h.put(it) }
        return JSONObject()
            .put("id", id).put("component", component).put("category", category).put("title", title)
            .put("evidence", evidence).put("severity", severity).put("confidence", confidence)
            .put("impact", impact).put("remediation", remediation).put("verification", verification)
            .put("status", status).put("history", h).put("firstSeen", firstSeen).put("lastSeen", lastSeen)
    }
}
