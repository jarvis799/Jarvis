package com.jarvis.assistant.ui.devstudio

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.jarvis.assistant.R
import com.jarvis.assistant.devstudio.AiProjectBrain
import com.jarvis.assistant.devstudio.DevStudioPreferences
import com.jarvis.assistant.devstudio.GitHubDevClient
import com.jarvis.assistant.devstudio.ProjectFile
import com.jarvis.assistant.devstudio.ProjectJob
import com.jarvis.assistant.devstudio.ProjectJobRunner
import com.jarvis.assistant.devstudio.ProjectJobService
import com.jarvis.assistant.devstudio.ProjectPlan
import com.jarvis.assistant.devstudio.ProjectStage
import com.jarvis.assistant.devstudio.ProjectStore
import com.jarvis.assistant.devstudio.ProjectWorkspace
import com.jarvis.assistant.devstudio.VercelClient
import com.jarvis.assistant.terminal.TerminalManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Planning conversation, staged generation with progress, validation report, ZIP export,
 * GitHub push and Vercel deploy. Every external action asks for approval first.
 */
class ProjectStudioActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_JOB_ID = "job_id"
        private const val REQ_VOICE = 4101
        private const val REQ_ZIP = 4102
        private const val PREVIEW_URL = "http://127.0.0.1:5173"
    }

    private val preferences by lazy { DevStudioPreferences(this) }
    private val store by lazy { ProjectStore(this) }
    private val workspace by lazy { ProjectWorkspace(this) }
    private val runner by lazy { ProjectJobRunner(this) }

    private var jobId: String? = null
    private var pendingZip: File? = null
    private var plan: ProjectPlan? = null
    private val answers = mutableListOf<String>()
    private var poller: Job? = null
    private var busy = false

    override fun onCreate(savedInstanceState: Bundle?) {
        com.jarvis.assistant.util.ThemeManager.applyTheme(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dev_project)
        com.jarvis.assistant.util.ThemeManager.applyDynamicBackground(findViewById(android.R.id.content))

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<View>(R.id.btnPlan).setOnClickListener { beginPlanning() }
        findViewById<View>(R.id.btnAnswer).setOnClickListener { answer(false) }
        findViewById<View>(R.id.btnSkip).setOnClickListener { answer(true) }
        findViewById<View>(R.id.btnMakePlan).setOnClickListener { makePlan() }
        findViewById<View>(R.id.btnMic).setOnClickListener { listen() }
        findViewById<View>(R.id.btnCancelJob).setOnClickListener {
            ProjectJobService.cancel(this)
            findViewById<TextView>(R.id.tvStep).text = "Cancellation requested. The run stops after the current step."
        }
        findViewById<View>(R.id.btnExport).setOnClickListener { exportZip() }
        findViewById<View>(R.id.btnPublish).setOnClickListener { publishToGithub() }
        findViewById<View>(R.id.btnDeploy).setOnClickListener { deployToVercel() }
        findViewById<View>(R.id.btnPreview).setOnClickListener { startPreview() }
        findViewById<View>(R.id.btnStopPreview).setOnClickListener { stopPreview() }
        findViewById<View>(R.id.btnClear).setOnClickListener { clearScreen() }

        jobId = intent.getStringExtra(EXTRA_JOB_ID)
        jobId?.let { id ->
            store.find(id)?.let { job ->
                showJob(job)
                if (!job.stage.finished) startPolling()
            }
        }
        refreshHistory()
    }

    override fun onDestroy() {
        poller?.cancel()
        super.onDestroy()
    }

    // -------- planning conversation --------

    private fun beginPlanning() {
        val prompt = findViewById<EditText>(R.id.etPrompt).text.toString().trim()
        if (prompt.isBlank()) {
            DevStudioUi.toast(this, "Describe what you want to build first.")
            return
        }
        if (busy) return
        if (AiProjectBrain.isSimpleRequest(prompt)) {
            answers.clear()
            makePlan()
            return
        }
        answers.clear()
        askQuestion()
    }

    private fun askQuestion() {
        if (busy) return
        val prompt = findViewById<EditText>(R.id.etPrompt).text.toString().trim()
        busy = true
        findViewById<TextView>(R.id.tvStep).text = "Planning: question " + (answers.size + 1)
        lifecycleScope.launch {
            val result = runCatching { AiProjectBrain.nextQuestion(prompt, answers) }
            busy = false
            result.onSuccess { question ->
                findViewById<LinearLayout>(R.id.llQuestion).visibility = View.VISIBLE
                findViewById<TextView>(R.id.tvQuestion).text = question.ifBlank { AiProjectBrain.questions.first() }
                findViewById<EditText>(R.id.etAnswer).setText("")
            }.onFailure { findViewById<TextView>(R.id.tvStep).text = it.message ?: "Planning failed." }
        }
    }

    private fun answer(skipped: Boolean) {
        val text = findViewById<EditText>(R.id.etAnswer).text.toString().trim()
        answers.add(if (skipped || text.isBlank()) "(skipped)" else text)
        findViewById<EditText>(R.id.etAnswer).setText("")
        if (answers.size >= 6) makePlan() else askQuestion()
    }

    private fun makePlan() {
        if (busy) return
        val prompt = findViewById<EditText>(R.id.etPrompt).text.toString().trim()
        if (prompt.isBlank()) {
            DevStudioUi.toast(this, "Describe what you want to build first.")
            return
        }
        busy = true
        findViewById<TextView>(R.id.tvStep).text = "Building the project plan..."
        lifecycleScope.launch {
            val result = runCatching { AiProjectBrain.buildPlan(prompt, answers.toList()) }
            busy = false
            result.onSuccess { built ->
                plan = built
                findViewById<LinearLayout>(R.id.llQuestion).visibility = View.GONE
                findViewById<TextView>(R.id.tvPlan).apply {
                    visibility = View.VISIBLE
                    text = built.render()
                }
                findViewById<TextView>(R.id.tvStep).text = "Plan ready. Confirm to start generation."
                confirmPlan(built)
            }.onFailure {
                findViewById<TextView>(R.id.tvStep).text = it.message ?: "Could not build the plan."
            }
        }
    }

    private fun confirmPlan(built: ProjectPlan) {
        val message = built.render() + "\n\nGeneration runs in the background and files land in JarvisProjects/" + built.name + ". Start now?"
        AlertDialog.Builder(this)
            .setTitle("Confirm the plan")
            .setMessage(message)
            .setPositiveButton("Start") { _, _ -> startJob(built) }
            .setNegativeButton("Not now", null)
            .show()
    }

    private fun startJob(built: ProjectPlan) {
        val prompt = findViewById<EditText>(R.id.etPrompt).text.toString().trim()
        val job = runner.newJob(prompt, built)
        store.save(job)
        jobId = job.id
        showJob(job)
        ProjectJobService.start(this, job.id)
        findViewById<TextView>(R.id.tvStep).text = "Generating in the background..."
        startPolling()
    }

    // -------- live progress --------

    private fun startPolling() {
        poller?.cancel()
        poller = lifecycleScope.launch {
            while (isActive) {
                val current = jobId?.let { store.find(it) } ?: break
                showJob(current)
                if (current.stage.finished) break
                delay(1500)
            }
            refreshHistory()
        }
    }

    private fun showJob(job: ProjectJob) {
        val progress = findViewById<ProgressBar>(R.id.pbJob)
        progress.apply {
            visibility = if (job.stage.finished) View.GONE else View.VISIBLE
            this.progress = job.progress
        }
        findViewById<TextView>(R.id.tvStage).text = job.projectName + " · " + job.stage.label() + " · " + job.progress + "%\n" + job.message
        findViewById<TextView>(R.id.tvValidation).text = when {
            job.error.isNotBlank() -> "Failed: " + job.error
            job.validation.isNotBlank() -> job.validation
            else -> "Validation has not run yet."
        }
        findViewById<TextView>(R.id.tvLog).text = if (job.log.isEmpty()) "No activity yet." else job.log.joinToString("\n")

        val files = findViewById<LinearLayout>(R.id.llFiles)
        files.removeAllViews()
        if (job.filePaths.isEmpty()) DevStudioUi.emptyNote(files, "No files written yet.")
        else job.filePaths.forEach { path -> DevStudioUi.addRow(files, path, "") }

        findViewById<TextView>(R.id.tvResult).text = buildString {
            if (job.zipPath.isNotBlank()) append("ZIP: " + job.zipPath + "  (" + job.zipBytes / 1024 + " KB)\n")
            if (job.repoUrl.isNotBlank()) append("GitHub: " + job.repoUrl + "\n")
            if (job.deployUrl.isNotBlank()) append("Live: " + job.deployUrl)
            if (isBlank()) append("Output links appear here after export, push or deploy.")
        }
    }

    private fun refreshHistory() {
        val history = findViewById<LinearLayout>(R.id.llHistory)
        history.removeAllViews()
        val jobs = store.all()
        if (jobs.isEmpty()) {
            DevStudioUi.emptyNote(history, "No runs yet.")
            return
        }
        jobs.take(10).forEach { job ->
            DevStudioUi.addRow(history, job.projectName + " · " + job.stage.label(), job.message.ifBlank { job.prompt.take(60) }) {
                jobId = job.id
                showJob(job)
                if (!job.stage.finished) startPolling()
            }
        }
    }

    // -------- outputs --------

    private fun currentJob(): ProjectJob? = jobId?.let { store.find(it) }

    private fun exportZip() {
        val job = currentJob()
        if (job == null || job.zipPath.isBlank()) {
            DevStudioUi.toast(this, "Generate a project first - the ZIP is created after validation.")
            return
        }
        val zip = File(job.zipPath)
        if (!zip.isFile) {
            DevStudioUi.toast(this, "That ZIP is no longer on this device.")
            return
        }
        pendingZip = zip
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("application/zip")
            .putExtra(Intent.EXTRA_TITLE, zip.name)
        startActivityForResult(intent, REQ_ZIP)
    }

    private fun sourceFiles(job: ProjectJob): List<ProjectFile> {
        val paths = workspace.relativeFiles(job.projectName)
        return paths.mapNotNull { path -> workspace.readFile(job.projectName, path)?.let { ProjectFile(path, it) } }
    }

    private fun publishToGithub() {
        val job = currentJob() ?: return DevStudioUi.toast(this, "Nothing to publish yet.")
        if (job.filePaths.isEmpty()) return DevStudioUi.toast(this, "Generate a project first.")
        if (!preferences.hasGithub) {
            DevStudioUi.toast(this, "Connect GitHub first.")
            startActivity(Intent(this, GitHubConnectActivity::class.java))
            return
        }
        val saved = preferences.githubRepo
        val target = if (saved.isBlank()) job.projectName else saved
        val branch = "main"
        AlertDialog.Builder(this)
            .setTitle("Push to GitHub")
            .setMessage("Push " + job.filePaths.size + " files to " + target + " (" + branch + ")? Existing files in that repository keep their history.")
            .setPositiveButton("Push") { _, _ -> runPublish(job, target, branch) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun runPublish(job: ProjectJob, target: String, branch: String) {
        findViewById<TextView>(R.id.tvStep).text = "Pushing to GitHub..."
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val token = preferences.githubToken
                    val client = GitHubDevClient(token)
                    val repo = if (target.contains("/")) target
                    else client.createRepo(target, preferences.githubVisibility != "public")
                    val files = sourceFiles(job)
                    client.publish(repo, if (target.contains("/")) client.defaultBranch(repo) else branch, files,
                        "Jarvis Developer Studio: " + job.projectName)
                }
            }
            result.onSuccess { published ->
                val updated = job.copy(repoUrl = published.repoUrl)
                store.save(updated)
                showJob(updated)
                findViewById<TextView>(R.id.tvStep).text = "Pushed " + published.fileCount + " files."
                DevStudioUi.openUrl(this@ProjectStudioActivity, published.repoUrl)
            }.onFailure {
                findViewById<TextView>(R.id.tvStep).text = "GitHub push failed: " + (it.message ?: "unknown error")
            }
        }
    }

    private fun deployToVercel() {
        val job = currentJob() ?: return DevStudioUi.toast(this, "Nothing to deploy yet.")
        if (job.filePaths.isEmpty()) return DevStudioUi.toast(this, "Generate a project first.")
        if (!preferences.hasVercel) {
            DevStudioUi.toast(this, "Connect Vercel first.")
            startActivity(Intent(this, VercelConnectActivity::class.java))
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Deploy to Vercel")
            .setMessage("Upload " + job.filePaths.size + " files as project \"" + job.projectName + "\"?")
            .setPositiveButton("Preview") { _, _ -> runDeploy(job, "preview") }
            .setNeutralButton("Production") { _, _ -> runDeploy(job, "production") }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun runDeploy(job: ProjectJob, target: String) {
        findViewById<TextView>(R.id.tvStep).text = "Deploying to Vercel (" + target + ")..."
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { VercelClient(preferences.vercelToken).deploy(job.projectName, sourceFiles(job), target) }
            }
            result.onSuccess { deployed ->
                val updated = job.copy(deployUrl = deployed.url)
                store.save(updated)
                showJob(updated)
                findViewById<TextView>(R.id.tvStep).text = "Vercel " + deployed.state.lowercase() + " · " + deployed.fileCount + " files"
                DevStudioUi.openUrl(this@ProjectStudioActivity, deployed.url)
            }.onFailure {
                findViewById<TextView>(R.id.tvStep).text = "Vercel deploy failed: " + (it.message ?: "unknown error")
            }
        }
    }

    private fun startPreview() {
        val job = currentJob() ?: return DevStudioUi.toast(this, "Generate a project first.")
        val dir = workspace.projectDir(job.projectName)
        if (!File(dir, "package.json").isFile) {
            return DevStudioUi.toast(this, "This project type is not served by a local dev server.")
        }
        findViewById<TextView>(R.id.tvStep).text = "Starting the local preview server..."
        lifecycleScope.launch {
            val probe = withContext(Dispatchers.IO) { TerminalManager.runSuspend("node -v", 20000) }
            if (probe.exitCode != 0) {
                findViewById<TextView>(R.id.tvStep).text =
                    "Node.js is not installed in the local environment, so no local preview was started."
                return@launch
            }
            TerminalManager.run("cd " + dir.absolutePath + " && npm run dev -- --host 127.0.0.1 --port 5173", 600000)
            delay(4000)
            findViewById<TextView>(R.id.tvStep).text = "Preview server started on " + PREVIEW_URL
            DevStudioUi.openUrl(this@ProjectStudioActivity, PREVIEW_URL)
        }
    }

    private fun stopPreview() {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { TerminalManager.runSuspend("pkill -f vite || true", 20000) }
            findViewById<TextView>(R.id.tvStep).text =
                if (result.exitCode == 0) "Local preview server stopped." else "No local preview server was running."
        }
    }

    private fun clearScreen() {
        poller?.cancel()
        jobId = null
        plan = null
        answers.clear()
        findViewById<EditText>(R.id.etPrompt).setText("")
        findViewById<EditText>(R.id.etAnswer).setText("")
        findViewById<LinearLayout>(R.id.llQuestion).visibility = View.GONE
        findViewById<TextView>(R.id.tvPlan).apply { visibility = View.GONE; text = "" }
        findViewById<ProgressBar>(R.id.pbJob).visibility = View.GONE
        findViewById<TextView>(R.id.tvStage).text = ""
        findViewById<TextView>(R.id.tvValidation).text = ""
        findViewById<TextView>(R.id.tvLog).text = ""
        findViewById<TextView>(R.id.tvResult).text = ""
        findViewById<LinearLayout>(R.id.llFiles).removeAllViews()
        findViewById<TextView>(R.id.tvStep).text = "Describe what you want to build."
    }

    private fun listen() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak your request")
        runCatching { startActivityForResult(intent, REQ_VOICE) }
            .onFailure { DevStudioUi.toast(this, "Voice input is not available on this device.") }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != Activity.RESULT_OK) return
        when (requestCode) {
            REQ_VOICE -> {
                val spoken = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull().orEmpty()
                if (spoken.isBlank()) return
                val questionVisible = findViewById<LinearLayout>(R.id.llQuestion).visibility == View.VISIBLE
                if (questionVisible) findViewById<EditText>(R.id.etAnswer).setText(spoken)
                else findViewById<EditText>(R.id.etPrompt).setText(spoken)
            }
            REQ_ZIP -> {
                val uri = data?.data ?: return
                val zip = pendingZip ?: return
                val ok = runCatching { contentResolver.openOutputStream(uri)?.use { out -> zip.inputStream().use { it.copyTo(out) } } != null }
                    .getOrDefault(false)
                findViewById<TextView>(R.id.tvResult).text = if (ok) "ZIP saved to the location you chose." else "Could not write the ZIP there."
            }
        }
    }
}
