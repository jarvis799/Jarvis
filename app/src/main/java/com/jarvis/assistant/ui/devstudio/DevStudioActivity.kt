package com.jarvis.assistant.ui.devstudio

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.jarvis.assistant.R
import com.jarvis.assistant.devstudio.DevStudioPreferences
import com.jarvis.assistant.devstudio.ProjectJobService
import com.jarvis.assistant.devstudio.ProjectStore
import com.jarvis.assistant.devstudio.ProjectWorkspace

/** Entry point of the AI Developer Studio. */
class DevStudioActivity : AppCompatActivity() {

    private val preferences by lazy { DevStudioPreferences(this) }
    private val store by lazy { ProjectStore(this) }
    private val workspace by lazy { ProjectWorkspace(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        com.jarvis.assistant.util.ThemeManager.applyTheme(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dev_studio)
        com.jarvis.assistant.util.ThemeManager.applyDynamicBackground(findViewById(android.R.id.content))

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<View>(R.id.rowGithub).setOnClickListener {
            startActivity(Intent(this, GitHubConnectActivity::class.java))
        }
        findViewById<View>(R.id.rowVercel).setOnClickListener {
            startActivity(Intent(this, VercelConnectActivity::class.java))
        }
        findViewById<View>(R.id.rowProject).setOnClickListener {
            startActivity(Intent(this, ProjectStudioActivity::class.java))
        }
        findViewById<View>(R.id.rowBrowser).setOnClickListener {
            startActivity(Intent(this, BrowserAutomationActivity::class.java))
        }
        findViewById<View>(R.id.tvResumeJob).setOnClickListener { view ->
            val pending = store.resumable() ?: return@setOnClickListener
            ProjectJobService.start(this, pending.id)
            startActivity(Intent(this, ProjectStudioActivity::class.java).putExtra(ProjectStudioActivity.EXTRA_JOB_ID, pending.id))
            view.visibility = View.GONE
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        findViewById<TextView>(R.id.rowGithubStatus).text =
            if (preferences.hasGithub) "Connected as " + preferences.githubUsername else "Not connected"
        findViewById<TextView>(R.id.rowVercelStatus).text =
            if (preferences.hasVercel) "Token saved" else "Not connected"
        findViewById<TextView>(R.id.rowBrowserStatus).text =
            if (preferences.browserAutomationEnabled) "Enabled" else "Switched off"

        findViewById<TextView>(R.id.tvStudioStatus).text = buildString {
            append("AI provider: ")
            append(if (com.jarvis.assistant.JarvisApp.instance.preferences.let { it.apiKey.isNotBlank() || it.customEndpoint.isNotBlank() }) "configured" else "not configured (Settings)")
            append("\nCredential storage: ")
            append(if (preferences.storageAvailable) "encrypted and ready" else "unavailable on this device")
        }

        val projects = workspace.listProjects()
        findViewById<TextView>(R.id.tvProjects).text =
            if (projects.isEmpty()) "No projects yet. Open Project Studio to create one."
            else "Projects on this device:\n" + projects.joinToString("\n") {
                it.name + "  ·  " + it.walkTopDown().filter { file -> file.isFile }.count() + " files"
            }

        val pending = store.resumable()
        findViewById<TextView>(R.id.tvResumeJob).apply {
            if (pending == null) {
                visibility = View.GONE
            } else {
                visibility = View.VISIBLE
                text = "Interrupted run: " + pending.projectName + " (" + pending.stage.label() + "). Tap to resume."
            }
        }
    }
}
