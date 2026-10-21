package com.jarvis.assistant.security.cyberlab

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * MODULE 6 + 7 — Self-evolving research engine + creator approval protocol.
 * Turns real evidence (security findings, blocked commands, skill gaps) into
 * concrete proposals and asks the creator for explicit approval before any
 * meaningful change. Approval of one task never authorizes another.
 */
class ResearchEngine(private val context: Context) {

    data class Proposal(
        val id: String,
        val title: String,
        val problem: String,
        val evidence: String,
        val solution: String,
        val risks: String,
        val permissions: String,
        val needsApkRebuild: Boolean,
        val status: String,
        val created: Long
    )

    private fun file() = File(context.filesDir, "jarvis_research_proposals.json")

    fun all(): List<Proposal> {
        val f = file()
        if (!f.exists()) return emptyList()
        return try {
            val arr = JSONArray(f.readText())
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Proposal(
                    id = o.optString("id"), title = o.optString("title"), problem = o.optString("problem"),
                    evidence = o.optString("evidence"), solution = o.optString("solution"),
                    risks = o.optString("risks"), permissions = o.optString("permissions"),
                    needsApkRebuild = o.optBoolean("needsApkRebuild", false),
                    status = o.optString("status", "PENDING"), created = o.optLong("created")
                )
            }
        } catch (e: Exception) { emptyList() }
    }

    private fun save(list: List<Proposal>) {
        val arr = JSONArray()
        list.forEach { p ->
            arr.put(
                JSONObject().put("id", p.id).put("title", p.title).put("problem", p.problem)
                    .put("evidence", p.evidence).put("solution", p.solution).put("risks", p.risks)
                    .put("permissions", p.permissions).put("needsApkRebuild", p.needsApkRebuild)
                    .put("status", p.status).put("created", p.created)
            )
        }
        file().writeText(arr.toString())
    }

    /** Drafts an evidence-backed proposal from the strongest open security finding, if any. */
    fun draftProposal(findings: List<FindingsStore.Finding>): Proposal? {
        val ranked = FindingsStore.SEVERITY_ORDER
        val open = findings.filter { it.status != "FIXED" }
            .sortedBy { f -> ranked.indexOf(f.severity).let { if (it < 0) 99 else it } }
        val base = open.firstOrNull() ?: return null
        val p = Proposal(
            id = UUID.randomUUID().toString(),
            title = "Remediate: " + base.title,
            problem = "Security finding [" + base.severity + "/" + base.confidence + "] in " + base.component + ": " + base.title,
            evidence = base.evidence,
            solution = base.remediation,
            risks = "Low - the change is scoped to " + base.component + " and the finding history keeps the previous state for rollback.",
            permissions = "No new Android permissions required.",
            needsApkRebuild = false,
            status = "PENDING",
            created = System.currentTimeMillis()
        )
        val list = all().toMutableList()
        list.add(p)
        if (list.size > 50) list.removeAll(list.take(list.size - 50))
        save(list)
        return p
    }

    /** MODULE 7 — the exact approval question asked through voice/text. */
    fun approvalLine(creatorName: String, p: Proposal): String {
        val who = creatorName.trim().ifBlank { "Sir" }
        return who + ", I found a way to improve my cybersecurity analysis and tool reliability. " +
            "Problem: " + p.problem + ". Evidence: " + p.evidence + ". Proposed change: " + p.solution + ". " +
            "Risks: " + p.risks + " Permissions: " + p.permissions + " " +
            (if (p.needsApkRebuild) "An APK rebuild is needed. " else "No APK rebuild is needed. ") +
            "Shall I apply it?"
    }

    fun setApproval(id: String, approved: Boolean): Proposal? {
        val list = all().toMutableList()
        val idx = list.indexOfFirst { it.id == id }
        if (idx < 0) return null
        val updated = list[idx].copy(status = if (approved) "APPROVED" else "DECLINED")
        list[idx] = updated
        save(list)
        return updated
    }

    fun clear() { file().delete() }

    /** What the gateway audit says about blocked or risky command attempts. */
    fun gatewayInsights(): String {
        return runCatching { ExecutionGateway.auditLog() }.getOrDefault("[]")
    }
}
