package com.jarvis.assistant.devstudio

import com.jarvis.assistant.JarvisApp
import com.jarvis.assistant.browser.BrowserSessionManager
import com.jarvis.assistant.terminal.TerminalManager

/**
 * Browser automation built on the runtime the user installs once (the MIT-licensed
 * meta-automation runtime runs inside the app's local Linux environment, not a new server).
 * External actions always need explicit approval and CAPTCHA bypass is refused.
 */
object BrowserAutomation {

    private const val RUNTIME_DIR = "/root/browser-runtime"

    enum class RuntimeTask { STATUS, SCAN, ANALYZE, PLAN_ONLY }

    data class RunResult(val ran: Boolean, val task: RuntimeTask, val verified: Boolean, val evidence: String, val output: String)

    private val preferences by lazy { DevStudioPreferences(JarvisApp.instance) }

    val installCommand: String
        get() = "apk add --no-cache nodejs npm git chromium && " +
            "git clone --depth 1 https://github.com/sunmughan/meta-automation.git " + RUNTIME_DIR + " && " +
            "cd " + RUNTIME_DIR + " && npm install --omit=dev"

    fun isEnabled(): Boolean = preferences.browserAutomationEnabled

    fun taskFor(command: String): RuntimeTask {
        val t = command.lowercase()
        return when {
            t.contains("status") || t.contains("obostha") || t.contains("running") -> RuntimeTask.STATUS
            t.contains("scan") || t.contains("khujo") || t.contains("inspect") || t.contains("check") || t.contains("search") -> RuntimeTask.SCAN
            t.contains("analy") || t.contains("review") || t.contains("bishleshon") -> RuntimeTask.ANALYZE
            else -> RuntimeTask.PLAN_ONLY
        }
    }

    /** Local runtime status - reported honestly, never assumed. */
    suspend fun runtimeStatus(): String {
        if (!TerminalManager.isLinuxEnvReady()) return "Local Linux environment not set up yet (open the Terminal tool once)."
        val node = TerminalManager.runSuspend("node -v", 20000)
        if (node.exitCode != 0) return "Node.js runtime not installed yet."
        val installed = TerminalManager.runSuspend("test -d " + RUNTIME_DIR + " && echo present", 15000)
        return if (installed.output.contains("present")) "Runtime ready - Node " + node.output.trim()
        else "Node.js ready, browser runtime not installed yet."
    }

    suspend fun installRuntime(onLog: (String) -> Unit): Boolean {
        onLog("Installing browser runtime (needs a network connection and about 400 MB).")
        val result = TerminalManager.runSuspend(installCommand, 600000)
        onLog(result.output.takeLast(600))
        return result.exitCode == 0
    }

    /**
     * Runs one supported runtime verb. Tasks the runtime does not expose locally are
     * reported as a plan instead of pretending they executed.
     */
    suspend fun runTask(command: String, approved: Boolean, onLog: (String) -> Unit): RunResult {
        val refusal = DevStudioRules.bypassRefusal(command)
        if (refusal != null) return RunResult(false, RuntimeTask.PLAN_ONLY, false, refusal, refusal)
        if (!isEnabled()) return RunResult(false, RuntimeTask.PLAN_ONLY, false, "Browser Automation is switched off in Settings.", "")

        val kind = DevStudioRules.classifyBrowserAction(command)
        if (DevStudioRules.requiresApproval(kind) && !approved) {
            return RunResult(false, RuntimeTask.PLAN_ONLY, false, DevStudioRules.approvalPrompt(kind, "the website"), "")
        }

        val task = taskFor(command)
        if (task == RuntimeTask.PLAN_ONLY) {
            return RunResult(false, task, false, "This action needs a supported runtime mode. Supported modes: status, scan, analyze.", "")
        }

        val verb = when (task) {
            RuntimeTask.STATUS -> "status"
            RuntimeTask.SCAN -> "scan"
            else -> "analyze"
        }
        onLog("Running browser task: " + verb)
        val result = TerminalManager.runSuspend("cd " + RUNTIME_DIR + " && node threads-agent.js " + verb, 240000)
        onLog(result.output.takeLast(800))
        val output = result.output.trim()
        val verified = result.exitCode == 0 && output.isNotBlank()
        return RunResult(
            ran = true,
            task = task,
            verified = verified,
            evidence = if (verified) "Runtime finished with exit code 0 and printed output." else "No verified evidence: exit code " + result.exitCode + ".",
            output = output.takeLast(4000)
        )
    }

    fun recordSession(url: String, title: String) {
        runCatching { BrowserSessionManager(JarvisApp.instance).recordSession(url, title) }
    }
}
