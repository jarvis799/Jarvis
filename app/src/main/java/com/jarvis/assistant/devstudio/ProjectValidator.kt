package com.jarvis.assistant.devstudio

import com.jarvis.assistant.terminal.TerminalManager

/**
 * Validation that only reports what actually ran. A project is never called "built"
 * without a passing build command or an explicit statement that no build was run.
 */
object ProjectValidator {

    data class Report(val summary: String, val issues: List<String>, val buildLog: String)

    fun staticIssues(workspace: ProjectWorkspace, projectName: String, plan: ProjectPlan): List<String> {
        val files = workspace.relativeFiles(projectName)
        val issues = mutableListOf<String>()
        if (files.isEmpty()) {
            issues.add("No source files were written.")
            return issues
        }
        val lower = files.map { it.lowercase() }
        val stack = plan.stack.lowercase()
        if (stack.contains("react") || files.any { it.endsWith("package.json") }) {
            if (!lower.contains("package.json")) issues.add("Missing package.json for an npm project.")
            if (files.none { it.endsWith(".html") }) issues.add("Missing an HTML entry file.")
        }
        if (stack.contains("html") && files.none { it.endsWith(".html") }) issues.add("No HTML page was generated.")

        val reference = Regex("(?:href|src)\\s*=\\s*[\"']([^\"']+)[\"']")
        val imports = Regex("(?:from|import|require\\(|import\\()\\s*[\"'](\\.{1,2}/[^\"']+)[\"']")
        files.filter { it.endsWith(".html") || it.endsWith(".js") || it.endsWith(".jsx") || it.endsWith(".ts") || it.endsWith(".tsx") }
            .take(40)
            .forEach { path ->
                val content = workspace.readFile(projectName, path) ?: return@forEach
                val folder = path.substringBeforeLast('/', "")
                reference.findAll(content).forEach { match ->
                    val target = match.groupValues[1].trim()
                    if (target.isBlank() || target.startsWith("http") || target.startsWith("data:") ||
                        target.startsWith("#") || target.startsWith("mailto:")) return@forEach
                    val relative = if (folder.isBlank()) target else folder + "/" + target
                    if (!files.contains(relative)) issues.add(path + " points at missing " + target)
                }
                imports.findAll(content).forEach { match ->
                    val target = match.groupValues[1].trim()
                    val base = target.removePrefix("./")
                    val candidates = listOf(base, base + ".js", base + ".jsx", base + ".ts", base + ".tsx", base + "/index.js", base + "/index.jsx")
                    val folder2 = folder
                    val exists = candidates.any { c -> files.contains(if (folder2.isBlank()) c else folder2 + "/" + c) }
                    if (!exists) issues.add(path + " imports missing module " + target)
                }
            }
        return issues.distinct().take(20)
    }

    /** Runs npm install/build only when a local runtime exists; otherwise says so plainly. */
    suspend fun runBuild(projectName: String, onLog: (String) -> Unit): Pair<Boolean, String> {
        val workspace = ProjectWorkspace()
        val dir = workspace.projectDir(projectName)
        val hasPackage = java.io.File(dir, "package.json").isFile
        if (!hasPackage) return false to "No package.json, so no npm build was required."
        if (!TerminalManager.isLinuxEnvReady()) {
            return false to "Files generated. Local build tooling is not available (Linux environment not set up), so no build was run."
        }
        val probe = TerminalManager.runSuspend("node -v && npm -v", 60000)
        if (probe.exitCode != 0) {
            return false to "Files generated. Node.js/npm are not installed on this device, so no build was run."
        }
        onLog("Running npm install...")
        val install = TerminalManager.runSuspend("cd " + dir.absolutePath + " && npm install --no-audit --no-fund", 420000)
        onLog(install.output.takeLast(500))
        if (install.exitCode != 0) {
            return false to "npm install failed (exit " + install.exitCode + "). Files are kept so you can retry."
        }
        onLog("Running npm run build...")
        val build = TerminalManager.runSuspend("cd " + dir.absolutePath + " && npm run build", 420000)
        onLog(build.output.takeLast(500))
        val passed = build.exitCode == 0
        return passed to if (passed) "Validated: npm install and npm run build both passed."
        else "npm run build failed (exit " + build.exitCode + "). Files are kept; see the build log."
    }
}
