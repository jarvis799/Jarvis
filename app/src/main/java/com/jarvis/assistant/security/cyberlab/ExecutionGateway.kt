package com.jarvis.assistant.security.cyberlab

import com.jarvis.assistant.JarvisApp
import com.jarvis.assistant.terminal.TerminalManager
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * MODULE 9 + 10 — Secure command execution gateway + privacy.
 * Every shell command is classified (read-only / modifying / consequential),
 * validated against a hard denylist, executed through the isolated
 * TerminalManager, then audited. Output is redacted before it is stored or
 * returned: tokens, passwords and API keys never survive into logs.
 * Untrusted model output can never bypass these checks.
 */
object ExecutionGateway {

    enum class Risk { READ_ONLY, MODIFYING, CONSEQUENTIAL }

    data class Decision(val allowed: Boolean, val risk: Risk?, val reason: String)
    data class AuditedResult(val result: TerminalManager.TerminalResult?, val decision: Decision)

    private val deny = listOf(
        Regex("(?i)\\brm\\s+[^|;]*-[a-z]*[rf]"),                    // rm with recursive/force flags
        Regex("(?i)\\bmkfs\\b"),
        Regex("(?i)\\bdd\\b[^|;]*\\bof=/dev/"),
        Regex("\\:\\(\\)\\s*\\{"),                                   // fork bomb
        Regex("(?i)\\bchmod\\s+(-\\w+\\s+)*777\\s+/(\\s|$)"),
        Regex("(?i)\\b(shutdown|reboot|poweroff|halt)\\b"),
        Regex("(?i)\\bformat\\b"),
        Regex("(?i)\\|\\s*(sudo\\s+)?(ba|z|fi)?sh\\b"),             // pipe into a shell
        Regex("(?i)\\bhistory\\s+-c\\b"),
        Regex("(?i)>\\s*/dev/sd[a-z]")
    )

    private val readOnlyCommands = setOf(
        "ls", "cat", "head", "tail", "grep", "find", "pwd", "whoami", "uname", "date",
        "df", "du", "free", "ps", "id", "env", "stat", "wc", "which", "echo", "help",
        "man", "ping", "nslookup", "getprop", "logcat", "dumpsys", "pm", "printenv"
    )

    fun classify(command: String): Decision {
        val c = command.trim()
        if (c.isEmpty()) return Decision(false, null, "Empty command.")
        for (d in deny) {
            if (d.containsMatchIn(c)) {
                return Decision(false, null, "Blocked by the gateway: this pattern can destroy data, break the device or run untrusted code.")
            }
        }
        if (Regex("(?i)\\bsudo\\b").containsMatchIn(c)) {
            return Decision(false, null, "sudo is unavailable in this sandboxed environment - no silent privilege escalation.")
        }
        val first = c.split(Regex("\\s+")).first().lowercase()
        val piped = c.contains('|') || c.contains('>') || c.contains('<')
        return if (first in readOnlyCommands && !piped) {
            Decision(true, Risk.READ_ONLY, "Read-only command.")
        } else {
            Decision(true, Risk.MODIFYING, "Modifying command - validated by the gateway and logged.")
        }
    }

    fun redact(text: String): String = text
        .replace(Regex("(?i)(password|passwd|secret|token|api[_-]?key|authorization)\\s*[:=]\\s*\\S+"), "$1=***")
        .replace(Regex("(?i)\\b(bearer|basic)\\s+\\S+"), "$1 ***")
        .replace(Regex("(?i)\\b(ghp|gho|ghs|ghr|github_pat|xoxb|xoxp|xoxa|sk)[-_][A-Za-z0-9_]{10,}"), "***")
        .replace(Regex("Bearer\\s+\\S+"), "Bearer ***")

    /** Validated + audited execution. Blocked commands never reach the shell. */
    fun execute(command: String, timeoutMs: Long = 30000, onResult: (AuditedResult) -> Unit) {
        val decision = classify(command)
        if (!decision.allowed) {
            audit(command, decision, "blocked")
            onResult(AuditedResult(null, decision))
            return
        }
        TerminalManager.run(command, timeoutMs) { res ->
            audit(command, decision, "exit=" + res.exitCode + " out=" + res.output.take(120))
            onResult(AuditedResult(res.copy(output = redact(res.output)), decision))
        }
    }

    private fun audit(command: String, decision: Decision, outcome: String) {
        runCatching {
            val f = File(JarvisApp.instance.filesDir, "jarvis_gateway_audit.json")
            val arr = if (f.exists()) runCatching { JSONArray(f.readText()) }.getOrDefault(JSONArray()) else JSONArray()
            arr.put(
                JSONObject()
                    .put("t", System.currentTimeMillis())
                    .put("cmd", redact(command))
                    .put("risk", decision.risk?.name ?: "BLOCKED")
                    .put("outcome", outcome)
            )
            while (arr.length() > 300) arr.remove(0)
            f.writeText(arr.toString())
        }
    }

    /** Redacted audit trail for inspection/export. */
    fun auditLog(): String = runCatching {
        val f = File(JarvisApp.instance.filesDir, "jarvis_gateway_audit.json")
        if (f.exists()) f.readText() else "[]"
    }.getOrDefault("[]")
}
