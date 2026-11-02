package com.jarvis.assistant.automation.python

import android.util.Base64
import com.jarvis.assistant.JarvisApp
import com.jarvis.assistant.terminal.TerminalManager
import java.io.File

/**
 * DEV STYLE: real Python execution for Jarvis.
 *
 * Scripts run inside the bundled Alpine Linux (proot) with python3 + pip3 pre-installed, so any
 * snippet the user pastes - bots, scrapers, automation, data work - really executes and its
 * output comes back. Nothing is simulated.
 */
object PythonSnippetRunner {

    data class RunResult(
        val ok: Boolean,
        val output: String,
        val exitCode: Int,
        val scriptPath: String
    )

    /** Runs a saved snippet: installs its dependencies first, then the script. */
    suspend fun run(snippet: PythonSnippet): RunResult {
        if (snippet.code.isBlank()) {
            return RunResult(false, "This snippet has no code yet.", -1, "")
        }
        if (snippet.dependencies.isNotBlank()) {
            val install = TerminalManager.runSuspend("pip3 install " + snippet.dependencies.trim(), 900_000)
            if (install.exitCode != 0) {
                return RunResult(
                    false,
                    "Dependency install failed:\n" + install.output.takeLast(1200),
                    install.exitCode,
                    ""
                )
            }
        }
        return runCode(snippet.code, snippet.name)
    }

    /** Runs arbitrary Python for a one-off request. */
    suspend fun runCode(code: String, name: String = "snippet"): RunResult {
        if (code.isBlank()) return RunResult(false, "No Python code to run.", -1, "")
        val safeName = name.lowercase()
            .replace(Regex("[^a-z0-9_]+"), "_")
            .trim('_')
            .ifBlank { "snippet" }
            .take(40)
        val remotePath = "/root/jarvis_$safeName.py"
        val encoded = Base64.encodeToString(code.toByteArray(), Base64.NO_WRAP)
        val write = TerminalManager.runSuspend("printf '%s' '$encoded' | base64 -d > $remotePath", 180_000)
        if (write.exitCode != 0) {
            return RunResult(false, "Could not write the script:\n" + write.output.takeLast(800), write.exitCode, remotePath)
        }
        val result = TerminalManager.runSuspend("python3 $remotePath", 600_000)
        return RunResult(result.exitCode == 0, result.output, result.exitCode, remotePath)
    }

    /** Copies the last script to shared storage so the user can keep it. */
    fun scriptDir(): File {
        val dir = File(TerminalManager.workspaceDir(), "python")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    suspend fun runSaved(context: android.content.Context, snippet: PythonSnippet): RunResult {
        val result = run(snippet)
        val status = if (result.ok) "OK · ${result.output.take(120)}" else "Failed · ${result.output.take(120)}"
        PythonSnippetStore.recordRun(context, snippet.id, status)
        return result
    }

    /** Used by the assistant: run by saved name, with a readable answer either way. */
    suspend fun runByName(context: android.content.Context, name: String): String {
        val snippet = PythonSnippetStore.findByName(context, name)
            ?: return "No saved Python snippet named \"$name\". Open Python Automation to add one; nothing was run."
        val result = runSaved(context, snippet)
        val head = if (result.ok) "Ran \"${snippet.name}\" successfully." else "Script \"${snippet.name}\" exited with code ${result.exitCode}."
        return head + "\n" + result.output.takeLast(2500)
    }

    /** Used by the assistant: run code the model wrote for this turn. */
    suspend fun runInline(code: String): String {
        val result = runCode(code)
        val head = if (result.ok) "Python finished successfully." else "Python exited with code ${result.exitCode}."
        return head + "\n" + result.output.takeLast(2500)
    }
}