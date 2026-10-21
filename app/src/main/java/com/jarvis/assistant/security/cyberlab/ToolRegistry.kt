package com.jarvis.assistant.security.cyberlab

import android.content.Context
import org.json.JSONArray

/**
 * Tool registry for the Cyber Lab.
 * Every entry maps to something the lab can actually run, and network-capable
 * tools go through the Authorized Assessment gate before they are used.
 */
class ToolRegistry(context: Context) {

    data class Tool(
        val id: String,
        val name: String,
        val category: String,
        val description: String,
        val risk: String,
        val requiresNetwork: Boolean,
        val requiresAuthorization: Boolean,
        val requiresCreatorKey: Boolean,
        val offline: Boolean
    )

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun all(): List<Tool> = catalog

    fun byCategory(): Map<String, List<Tool>> = catalog.groupBy { it.category }

    fun get(id: String): Tool? = catalog.firstOrNull { it.id == id }

    fun enabled(id: String): Boolean = !disabled().contains(id)

    fun setEnabled(id: String, enabled: Boolean) {
        val list = disabled().toMutableList()
        if (enabled) {
            list.removeAll { it == id }
        } else if (!list.contains(id)) {
            list.add(id)
        }
        write(list)
    }

    fun canRun(toolId: String, host: String? = null): AuthorizedAssessmentMode.Decision {
        val tool = get(toolId) ?: return AuthorizedAssessmentMode.Decision(false, "Unknown tool: " + toolId)
        if (!enabled(toolId)) return AuthorizedAssessmentMode.Decision(false, tool.name + " is turned off in the registry.")
        if (tool.requiresAuthorization) {
            if (host.isNullOrBlank()) {
                return AuthorizedAssessmentMode.Decision(false, tool.name + " needs a host that is inside the declared scope.")
            }
            return AuthorizedAssessmentMode.gate(host)
        }
        return AuthorizedAssessmentMode.Decision(true, tool.name + " runs offline against pasted data only.")
    }

    fun report(): String {
        val builder = StringBuilder()
        byCategory().forEach { group ->
            builder.append(group.key).append(NL)
            group.value.forEach { tool ->
                builder.append(if (enabled(tool.id)) "  [on]  " else "  [off] ").append(tool.name)
                    .append(" - ").append(tool.risk)
                    .append(if (tool.requiresNetwork) " | network (authorized)" else " | offline")
                    .append(NL)
            }
        }
        return builder.toString()
    }

    private fun disabled(): List<String> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            val list = ArrayList<String>()
            for (index in 0 until array.length()) list.add(array.optString(index))
            list
        } catch (error: Throwable) {
            emptyList()
        }
    }

    private fun write(list: List<String>) {
        val array = JSONArray()
        list.forEach { array.put(it) }
        prefs.edit().putString(KEY, array.toString()).apply()
    }

    companion object {
        private const val PREFS = "jarvis_cyberlab"
        private const val KEY = "disabled_tools"
        private const val NL = "\n"

        val catalog = listOf(
            Tool("manifest-audit", "Manifest audit", "Static analysis", "Offline checks over a pasted AndroidManifest.xml: debug flags, backup, cleartext, exported components, permissions.", "PASSIVE", false, false, false, true),
            Tool("source-audit", "Source audit", "Static analysis", "Offline pattern checks over a pasted source file: credentials, weak crypto, TLS bypass, injection patterns.", "PASSIVE", false, false, false, true),
            Tool("findings-report", "Findings report", "Evidence", "Exports every finding with severity, confidence, status, evidence and fix guidance.", "PASSIVE", false, false, false, true),
            Tool("research-proposal", "Research proposal", "Self-improvement", "Drafts an evidence-backed improvement proposal and waits for the creator to approve or decline.", "PASSIVE", false, false, false, true),
            Tool("skill-pack-validate", "Skill pack validation", "Extensibility", "Validates and stores data-only skill packs with an integrity hash and rollback history.", "PASSIVE", false, false, false, true),
            Tool("gateway-policy", "Execution gateway policy", "Guardrails", "Deny-by-default command policy with redaction of everything logged or displayed.", "PASSIVE", false, false, false, true),
            Tool("header-check", "Authorized header check", "Network", "One passive HTTPS request to a host in declared scope: transport and response header review.", "LOW", true, true, true, false)
        )
    }
}
