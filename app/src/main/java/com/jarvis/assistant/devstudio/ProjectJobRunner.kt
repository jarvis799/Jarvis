package com.jarvis.assistant.devstudio

import android.content.Context
import com.jarvis.assistant.JarvisApp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Persistent, resumable project generation: plan -> files -> validation -> ZIP.
 * Progress is written to ProjectStore after every step, so an interrupted run can resume.
 */
class ProjectJobRunner(context: Context = JarvisApp.instance) {

    private val workspace = ProjectWorkspace(context)
    private val store = ProjectStore(context)
    private val prefs = DevStudioPreferences(context)
    private var job: Job? = null

    val isRunning: Boolean get() = job == null || job?.isActive == true
    fun cancel() { job?.cancel() }

    fun newJob(prompt: String, plan: ProjectPlan): ProjectJob = ProjectJob(
        id = UUID.randomUUID().toString(),
        prompt = prompt,
        projectName = workspace.uniqueProjectName(plan.name),
        plan = plan.copy(name = workspace.uniqueProjectName(plan.name)),
        stage = ProjectStage.PLANNING,
        message = "Waiting for confirmation"
    )

    /** Generates and packages a confirmed plan. */
    fun start(base: ProjectJob, onUpdate: (ProjectJob) -> Unit, onFinished: (ProjectJob) -> Unit) {
        cancel()
        job = CoroutineScope(Dispatchers.IO).launch {
            var current = base.copy(stage = ProjectStage.GENERATING, progress = 1, message = "Starting", error = "")
            fun step(update: ProjectJob) {
                current = update
                store.save(current)
                onUpdate(current)
            }
            fun note(line: String) {
                val stamp = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
                step(current.copy(log = (current.log + "[" + stamp + "] " + line).takeLast(200)))
            }
            try {
                val plan = current.plan ?: throw IllegalStateException("No confirmed plan for this project.")
                note("Plan confirmed: " + plan.name + " (" + plan.stack + ")")
                step(current.copy(message = "Planning files"))

                val manifest = AiProjectBrain.fileList(plan)
                if (manifest.isEmpty()) throw IllegalStateException("The AI produced no file list. Try again.")
                note("Manifest: " + manifest.size + " files")

                val written = mutableListOf<String>()
                val failed = mutableListOf<String>()
                var done = 0
                manifest.chunked(4).forEach { batch ->
                    ensureActive()
                    note("Writing " + batch.joinToString(", "))
                    val files = generate(plan, batch, failed, ::note)
                    val result = workspace.writeFiles(current.projectName, files, prefs.secrets)
                    written += result.first
                    result.second.forEach { skipped -> note("Skipped " + skipped) }
                    done += batch.size
                    step(current.copy(
                        progress = 2 + (done * 58 / manifest.size),
                        message = "Generating files (" + written.size + " of " + manifest.size + ")",
                        filePaths = written.distinct()
                    ))
                }
                manifest.filterNot { written.contains(it) }.forEach { failed.add(it) }

                step(current.copy(stage = ProjectStage.VALIDATING, progress = 65, message = "Validating"))
                note("Validating project")
                val staticIssues = ProjectValidator.staticIssues(workspace, current.projectName, plan)
                staticIssues.forEach { note("Check: " + it) }
                val (_, buildSummary) = ProjectValidator.runBuild(current.projectName, ::note)
                val validation = buildSummary + (if (staticIssues.isEmpty()) " No missing file or import problems found."
                    else " " + staticIssues.size + " file/import problem(s) still open.")
                step(current.copy(
                    filePaths = workspace.relativeFiles(current.projectName),
                    validation = validation,
                    progress = 80,
                    message = "Packaging"
                ))

                step(current.copy(stage = ProjectStage.PACKAGING, progress = 88, message = "Creating ZIP"))
                val zip = workspace.buildZip(current.projectName, includeBuildOutput = false)
                note("ZIP: " + zip.name + " (" + zip.length() + " bytes)")
                val entries = workspace.zipEntryNames(zip)
                note("ZIP contains " + entries.size + " files")

                step(current.copy(
                    stage = ProjectStage.COMPLETED,
                    progress = 100,
                    message = if (failed.isEmpty()) "Completed" else "Completed with " + failed.size + " missing file(s)",
                    zipPath = zip.absolutePath,
                    zipBytes = zip.length(),
                    filePaths = workspace.relativeFiles(current.projectName),
                    log = (current.log + (if (failed.isEmpty()) "[" + SimpleDateFormat("HH:mm:ss", Locale.US).format(Date()) + "] Done" else "[" + SimpleDateFormat("HH:mm:ss", Locale.US).format(Date()) + "] Missing: " + failed.joinToString(", "))).takeLast(200)
                ))
            } catch (cancelled: CancellationException) {
                step(current.copy(stage = ProjectStage.CANCELLED, message = "Cancelled", error = ""))
            } catch (e: Exception) {
                step(current.copy(stage = ProjectStage.FAILED, message = "Failed", error = e.message ?: "Unknown error"))
            }
            onFinished(current)
        }
    }

    private suspend fun generate(plan: ProjectPlan, batch: List<String>, failed: MutableList<String>, note: (String) -> Unit): List<ProjectFile> {
        var attempt = 0
        var lastError: String? = null
        while (attempt < 2) {
            try {
                return AiProjectBrain.generateBatch(plan, batch)
            } catch (e: Exception) {
                lastError = e.message
                attempt++
                note("Retry " + attempt + " for " + batch.joinToString(", "))
            }
        }
        failed += batch
        note("Could not generate: " + batch.joinToString(", ") + (if (lastError != null) " (" + lastError + ")" else ""))
        return emptyList()
    }

    /** Called when the app runs again after an interrupted job. */
    fun resumePending(onUpdate: (ProjectJob) -> Unit, onFinished: (ProjectJob) -> Unit): Boolean {
        val pending = store.resumable() ?: return false
        if (pending.plan == null) return false
        start(pending.copy(stage = ProjectStage.GENERATING, message = "Resuming interrupted run"), onUpdate, onFinished)
        return true
    }
}
