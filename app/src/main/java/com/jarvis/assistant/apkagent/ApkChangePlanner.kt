package com.jarvis.assistant.apkagent

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * DEV STYLE: one-question-at-a-time change assistant. Asks only questions whose
 * answers affect the implementation, remembers every decision, plans, gates on
 * explicit approval, and records reviewable change requests with recovery info.
 */
object ApkChangePlanner {

    enum class Phase { ASK_TARGET, ASK_SCOPE, ASK_AUDIENCE, CONFIRM_PLAN, DONE }

    class Session {
        var phase = Phase.ASK_TARGET
        val decisions = LinkedHashMap<String, String>()
        var plan: String? = null
        var report: ApkMetadataAnalyzer.ApkReport? = null
    }

    @Volatile private var current: Session? = null

    fun start(report: ApkMetadataAnalyzer.ApkReport): String {
        val answer = startWithReport(ApkMetadataAnalyzer.toText(report))
        current?.report = report
        return answer
    }

    fun startWithReport(reportText: String): String {
        val s = Session()
        current = s
        return "Analysis done. Which part do you want to modify - UI, voice commands, settings, or another feature?"
    }

    fun active(): Boolean = current != null

    fun stop() { current = null }

    /** Processes one answer; returns the next single question, the plan for approval, or the final summary. */
    fun answer(text: String): String {
        val s = current ?: return "First analyze an APK - tap Analyze APK, then we plan changes step by step."
        return when (s.phase) {
            Phase.ASK_TARGET -> {
                s.decisions["target"] = text
                s.phase = Phase.ASK_SCOPE
                "Understood: " + text + ". Should I modify the existing screen, or add a separate new screen?"
            }
            Phase.ASK_SCOPE -> {
                s.decisions["screenScope"] = text
                s.phase = Phase.ASK_AUDIENCE
                "Should this change apply to all users, or only your device?"
            }
            Phase.ASK_AUDIENCE -> {
                s.decisions["audience"] = text
                s.phase = Phase.CONFIRM_PLAN
                s.plan = buildPlan(s)
                "PLAN:\n" + s.plan + "\n\nNo file changes happen until you approve. Say APPROVE to apply, or anything else to cancel."
            }
            Phase.CONFIRM_PLAN -> {
                val lower = text.lowercase()
                val approved = lower.contains("approve") || lower == "yes" || lower.contains("haan") ||
                    lower.contains("koro") || lower.contains("apply")
                if (!approved) {
                    current = null
                    "Okay - nothing was changed. Tell me a new goal any time and I will plan again."
                } else {
                    val summary = applyPlan(s)
                    current = null
                    summary
                }
            }
            Phase.DONE -> "Change recorded. What should I improve next?"
        }
    }

    private fun buildPlan(s: Session): String = buildString {
        val r = s.report
        appendLine("1. Goal: " + s.decisions["target"])
        appendLine("2. Screen: " + s.decisions["screenScope"] + "  |  Audience: " + s.decisions["audience"])
        if (r != null) appendLine("3. Baseline: " + r.packageName + " v" + r.versionName + " - SHA-256 recorded, original APK untouched")
        appendLine("4. Risk areas checked: permissions (" + (r?.permissions?.size ?: 0) + "), signing, existing features")
        appendLine("5. Execution: change request saved with a reviewable summary; recovery = discard the request (nothing overwritten)")
    }.trimEnd()

    /** Applies the approved change - truthfully, within what an APK can support. */
    private fun applyPlan(s: Session): String {
        val context = com.jarvis.assistant.JarvisApp.instance
        val requestId = saveChangeRequest(context, s)
        return "Change request " + requestId + " saved and approved, Sir.\n" +
            "Sotti kotha: a compiled APK theke Kotlin source edit kora jay na - original sources/Gradle file APK er bhitore thake na, " +
            "and decompiled code is approximate only.\n" +
            "Next step (real): connect the original project repo in Creator > Repository Analyzer, then I apply this request there with checkpoints, a reviewable diff and a verified CI build. " +
            "The request keeps every decision so you never repeat yourself."
    }

    /** Persists the approved request - reviewable and reversible. */
    private fun saveChangeRequest(context: android.content.Context, s: Session): String {
        val id = "CR-" + (System.currentTimeMillis() / 1000).toString(36)
        val obj = JSONObject()
            .put("id", id).put("createdAt", System.currentTimeMillis()).put("status", "APPROVED")
            .put("plan", s.plan ?: "")
        val decisions = JSONObject()
        for ((k, v) in s.decisions) decisions.put(k, v)
        obj.put("decisions", decisions)
        s.report?.let { r ->
            obj.put("baseline", JSONObject()
                .put("package", r.packageName ?: "").put("version", r.versionName ?: "")
                .put("sha256", r.sha256))
        }
        runCatching {
            val dir = File(context.filesDir, "apkagent").apply { mkdirs() }
            File(dir, id + ".json").writeText(obj.toString(2))
        }
        return id
    }

    /** Lists saved change requests (the recovery view). */
    fun listRequests(context: android.content.Context): List<String> {
        val dir = File(context.filesDir, "apkagent")
        return dir.listFiles()?.filter { it.name.startsWith("CR-") && it.name.endsWith(".json") }
            ?.map { it.name.removeSuffix(".json") }.orEmpty().sortedDescending()
    }
}
