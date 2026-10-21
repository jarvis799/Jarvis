package com.jarvis.assistant.terminal

import com.jarvis.assistant.JarvisApp
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Termux-style shell runner. Executes commands in the background and keeps an output history. */
object TerminalManager {

    data class TerminalResult(val id: String, val command: String, val output: String, val exitCode: Int)

    // Commands that need a real Linux filesystem — routed through proot + Alpine rootfs.
    private val prootCommands = setOf(
        "apt", "apt-get", "pkg", "apk", "python", "python3", "pip", "pip3",
        "node", "nodejs", "npm", "npx", "gcc", "g++", "bash", "wget", "curl", "crontab", "git"
    )

    // Alpine packages JARVIS installs once, so python, node and downloads really work.
    private val toolPackages = mapOf(
        "python" to listOf("python3", "py3-pip"),
        "python3" to listOf("python3", "py3-pip"),
        "pip" to listOf("py3-pip"),
        "pip3" to listOf("py3-pip"),
        "node" to listOf("nodejs"),
        "nodejs" to listOf("nodejs"),
        "npm" to listOf("nodejs", "npm"),
        "npx" to listOf("nodejs", "npm"),
        "bash" to listOf("bash"),
        "wget" to listOf("wget", "ca-certificates"),
        "curl" to listOf("curl", "ca-certificates"),
        "git" to listOf("git", "git-perl", "ca-certificates"),
        "gcc" to listOf("build-base"),
        "g++" to listOf("build-base")
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val history = ArrayDeque<TerminalResult>()
    private val lock = Any()

    @Volatile
    private var workingDir: String = ""

    /** Writable workspace inside JARVIS's own storage - never the phone's read-only root. */
    fun workspaceDir(): File {
        val dir = File(JarvisApp.instance.filesDir, "jarvis_workspace")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun resolveTarget(target: String): File = when {
        target.isBlank() -> workspaceDir()
        target == "~" -> workspaceDir()
        target.startsWith("/") -> File(target)
        else -> File(currentDir(), target)
    }

    fun currentDir(): String = workingDir.ifBlank { workspaceDir().absolutePath }

    /** True when any command in the line needs the Linux rootfs, pipelines and scripts included. */
    private fun needsLinux(cmd: String): Boolean {
        val tokens = cmd.replace("\\", " ")
            .split(Regex("[\\s;|&()<>]+"))
            .map { it.trim().substringAfterLast('/').lowercase() }
        return tokens.any { it.isNotBlank() && it in prootCommands }
    }

    /** Installs python / node / download tools inside Alpine the first time they are asked for. */
    private fun ensureTooling(command: String, log: StringBuilder): Boolean {
        val wanted = toolPackages.filterKeys { tool ->
            Regex("(^|[\\s;|&(])" + Regex.escape(tool) + "([\\s;|&)]|$)").containsMatchIn(command)
        }
        if (wanted.isEmpty()) return true
        val missing = wanted.filter { (tool, _) -> !PRootBootstrap.hasTool(tool) }
        if (missing.isEmpty()) return true
        val names = missing.keys.joinToString(", ")
        log.appendLine("Setting up " + names + " inside the Linux environment (one time, internet lagbe)...")
        val packages = missing.values.flatten().distinct().joinToString(" ")
        val install = PRootBootstrap.runRaw("apk update >/dev/null 2>&1; apk add --no-cache " + packages)
        if (!install.first) {
            log.appendLine("Setup fail: " + install.second)
            return false
        }
        log.appendLine("Ready: " + names)
        return true
    }

    /** Fire-and-forget background execution so the UI and assistant stay responsive. */
    fun run(command: String, timeoutMs: Long = 30000, onDone: (TerminalResult) -> Unit = {}) {
        scope.launch { onDone(runSuspend(command, timeoutMs)) }
    }

    /** Explicit one-time environment setup (also happens automatically on first package command). */
    fun setupEnvironment(onProgress: (String) -> Unit = {}, onDone: (Boolean) -> Unit = {}) {
        scope.launch {
            val ready = runCatching { PRootBootstrap.setup(onProgress) }.getOrDefault(false)
            onDone(ready)
        }
    }

    fun isLinuxEnvReady(): Boolean = PRootBootstrap.isReady()

    /**
     * Maps Termux-style commands onto the Alpine rootfs and decides whether the
     * command needs proot. Returns (command, needsProot).
     */
    private fun routeCommand(cmd: String): Pair<String, Boolean> {
        val parts = cmd.split(Regex("\\s+"))
        val first = parts.firstOrNull()?.lowercase() ?: ""
        if (!needsLinux(cmd)) return cmd to false
        val mapped = when (first) {
            "apt", "apt-get", "pkg" -> {
                val rest = parts.drop(1)
                val adjusted = if (rest.firstOrNull()?.lowercase() == "install") listOf("add") + rest.drop(1) else rest
                "apk " + adjusted.joinToString(" ")
            }
            else -> cmd
        }
        return mapped to true
    }

    /** Suspend version for agent tools and services. */
    suspend fun runSuspend(command: String, timeoutMs: Long = 30000): TerminalResult = withContext(Dispatchers.IO) {
        val trimmed = command.trim()
        // cd persists across commands, like a real shell session.
        if (trimmed.startsWith("cd ") || trimmed == "cd") {
            val target = trimmed.removePrefix("cd").trim()
            val candidate = resolveTarget(target)
            val result = if (candidate.isDirectory) {
                workingDir = candidate.canonicalPath
                TerminalResult(newId(), command, "(cwd: " + candidate.canonicalPath + ")", 0)
            } else {
                TerminalResult(newId(), command, "cd: no such directory: " + target, 1)
            }
            addToHistory(result)
            return@withContext result
        }
        try {
            val (mapped, useProot) = routeCommand(trimmed)
            val bootstrapLog = StringBuilder()
            if (useProot && !PRootBootstrap.isReady()) {
                // First run: download proot + Alpine rootfs once (APK size stays the same).
                val ready = runCatching { PRootBootstrap.setup { bootstrapLog.appendLine(it) } }.getOrDefault(false)
                if (!ready) {
                    val result = TerminalResult(
                        newId(), command,
                        (bootstrapLog.toString().trim() + "\nEnvironment setup failed — " +
                                (PRootBootstrap.lastSetupError ?: "check internet connection") + ".").trim(),
                        -1
                    )
                    addToHistory(result)
                    return@withContext result
                }
            }
            if (useProot && !ensureTooling(mapped, bootstrapLog)) {
                val result = TerminalResult(newId(), command, bootstrapLog.toString().trim(), -1)
                addToHistory(result)
                return@withContext result
            }
            val detached = Regex("&\\s*$").containsMatchIn(trimmed)
            val effectiveTimeout = if (useProot) maxOf(timeoutMs, 600_000) else timeoutMs
            val pb = if (useProot) {
                ProcessBuilder(PRootBootstrap.launchCommand(mapped)).redirectErrorStream(true)
            } else {
                ProcessBuilder("sh", "-c", trimmed).redirectErrorStream(true)
            }
            pb.directory(File(currentDir()))
            if (useProot) {
                pb.environment()["HOME"] = "/root"
                pb.environment()["PATH"] = "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
                // DEV STYLE: no stored GitHub token is ever injected into the
                // terminal. git push uses the device owner's own credentials only -
                // a shared saved token would let anyone push to the token owner's
                // GitHub account.
            }
            val proc = pb.start()
            val out = StringBuilder()
            if (bootstrapLog.isNotBlank()) out.appendLine(bootstrapLog.toString().trim())
            if (detached) {
                // Trailing "&": bots and servers keep running in the background.
                val logFile = File(workspaceDir(), "background.log")
                out.appendLine("Ekhon background e cholche.")
                out.appendLine("Live output: " + logFile.absolutePath)
                val pump = Thread {
                    runCatching {
                        logFile.outputStream().bufferedWriter().use { writer ->
                            proc.inputStream.bufferedReader().forEachLine { line ->
                                writer.write(line)
                                writer.newLine()
                                writer.flush()
                            }
                        }
                    }
                }
                pump.isDaemon = true
                pump.start()
                val running = TerminalResult(newId(), command, out.toString().trim(), 0)
                addToHistory(running)
                return@withContext running
            }
            val reader = Thread { proc.inputStream.bufferedReader().forEachLine { out.appendLine(it) } }
            reader.start()
            val exited = proc.waitFor(effectiveTimeout, TimeUnit.MILLISECONDS)
            if (!exited) proc.destroyForcibly()
            reader.join(2000)
            val result = TerminalResult(
                newId(), command,
                out.toString().trim().ifBlank { "(no output)" },
                if (exited) proc.exitValue() else -1
            )
            addToHistory(result)
            result
        } catch (e: Exception) {
            val result = TerminalResult(newId(), command, "Error: " + (e.message ?: e.javaClass.simpleName), -1)
            addToHistory(result)
            result
        }
    }

    fun historyList(): List<TerminalResult> = synchronized(lock) { history.toList() }
    fun clearHistory() = synchronized(lock) { history.clear() }

    private fun addToHistory(r: TerminalResult) = synchronized(lock) {
        history.addLast(r)
        while (history.size > 200) history.removeFirst()
    }

    private fun newId() = UUID.randomUUID().toString()
}
