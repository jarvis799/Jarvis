package com.jarvis.assistant.bgremove

import com.jarvis.assistant.terminal.TerminalManager
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

data class PythonCapability(
    val environmentReady: Boolean,
    val pythonVersion: String?,
    val rembgInstalled: Boolean,
    val onnxInstalled: Boolean,
    val detail: String
) {
    val pythonRuns: Boolean get() = pythonVersion != null
}

/**
 * Asks the real terminal what it can do, instead of assuming desktop Python packages work on Android.
 * Every answer here comes from a command that actually ran.
 */
object LocalPythonProbe {

    private const val VERSION_COMMAND = "python3 --version"

    fun parseVersion(output: String): String? {
        val match = Regex("Python[ ]+3[.][0-9]+[.][0-9]+").find(output) ?: return null
        return match.value.trim()
    }

    fun parseModuleOk(output: String, module: String): Boolean = output.contains(module + " OK")

    fun looksMissingModule(output: String): Boolean =
        output.contains("No module named") || output.contains("ModuleNotFoundError")

    suspend fun probe(): PythonCapability {
        val environmentReady = runCatching { TerminalManager.isLinuxEnvReady() }.getOrDefault(false)
        val versionOutput = runCommand(VERSION_COMMAND, 90_000)
        val pythonVersion = parseVersion(versionOutput)
        if (pythonVersion == null) {
            val hint = if (environmentReady) "" else " The Linux environment has not finished setting up yet - open the terminal once."
            return PythonCapability(environmentReady, null, false, false, BgRemovalErrors.PYTHON_MISSING + hint)
        }
        val rembgOutput = runCommand("python3 -c \"import rembg; print('rembg OK')\"", 120_000)
        val onnxOutput = runCommand("python3 -c \"import onnxruntime; print('onnxruntime OK')\"", 120_000)
        val rembgInstalled = parseModuleOk(rembgOutput, "rembg")
        val onnxInstalled = parseModuleOk(onnxOutput, "onnxruntime")
        val detail = when {
            rembgInstalled && onnxInstalled -> "Python " + pythonVersion + " with rembg and onnxruntime is available on this phone."
            looksMissingModule(rembgOutput) -> BgRemovalErrors.REMBG_MISSING
            else -> "Python " + pythonVersion + " runs, but rembg could not be imported."
        }
        return PythonCapability(environmentReady, pythonVersion, rembgInstalled, onnxInstalled, detail)
    }

    private suspend fun runCommand(command: String, timeoutMs: Long): String =
        suspendCancellableCoroutine { continuation ->
            TerminalManager.run(command, timeoutMs) { result ->
                if (continuation.isActive) continuation.resume(result.output)
            }
            continuation.invokeOnCancellation { }
        }
}
