package com.jarvis.assistant.ui.devstudio

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.jarvis.assistant.R
import com.jarvis.assistant.devstudio.DevStudioPreferences
import com.jarvis.assistant.devstudio.GitHubDevClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Connect GitHub, verify the token and pick the repository used for pushes. */
class GitHubConnectActivity : AppCompatActivity() {

    private val preferences by lazy { DevStudioPreferences(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        com.jarvis.assistant.util.ThemeManager.applyTheme(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dev_github)
        com.jarvis.assistant.util.ThemeManager.applyDynamicBackground(findViewById(android.R.id.content))

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<EditText>(R.id.etGhUser).setText(preferences.githubUsername)
        findViewById<EditText>(R.id.etGhToken).setText(preferences.githubToken)
        findViewById<EditText>(R.id.etGhRepoName).setText(preferences.githubRepo)

        findViewById<View>(R.id.chipPrivate).setOnClickListener { setVisibility("private") }
        findViewById<View>(R.id.chipPublic).setOnClickListener { setVisibility("public") }
        setVisibility(preferences.githubVisibility)

        findViewById<View>(R.id.btnGhTest).setOnClickListener { testConnection() }
        findViewById<View>(R.id.btnGhSave).setOnClickListener { save() }
        findViewById<View>(R.id.btnGhDisconnect).setOnClickListener {
            preferences.disconnectGithub()
            status("GitHub disconnected. The stored token was deleted.")
            findViewById<EditText>(R.id.etGhToken).setText("")
        }
        if (preferences.hasGithub) loadRepos()
    }

    private fun setVisibility(value: String) {
        preferences.githubVisibility = value
        val selected = DevStudioUi.themeColor(this, R.attr.themeAccent)
        val normal = DevStudioUi.themeColor(this, R.attr.themeTextPrimary)
        findViewById<TextView>(R.id.chipPrivate).apply {
            setBackgroundResource(if (value == "private") R.drawable.bg_chip_selected else R.drawable.bg_jarvis_chip)
            setTextColor(if (value == "private") selected else normal)
        }
        findViewById<TextView>(R.id.chipPublic).apply {
            setBackgroundResource(if (value == "public") R.drawable.bg_chip_selected else R.drawable.bg_jarvis_chip)
            setTextColor(if (value == "public") selected else normal)
        }
    }

    private fun save() {
        val user = findViewById<EditText>(R.id.etGhUser).text.toString().trim()
        val token = findViewById<EditText>(R.id.etGhToken).text.toString().trim()
        if (token.isBlank()) {
            status("Enter your GitHub personal access token first.")
            return
        }
        runCatching {
            preferences.githubUsername = if (user.isBlank()) "github" else user
            preferences.githubToken = token
            preferences.githubRepo = findViewById<EditText>(R.id.etGhRepoName).text.toString().trim()
        }.onFailure { status(it.message ?: "Could not save the token.") }.onSuccess {
            status("Saved in encrypted storage. Testing the connection...")
            testConnection()
        }
    }

    private fun testConnection() {
        val token = findViewById<EditText>(R.id.etGhToken).text.toString().trim()
        if (token.isBlank()) {
            status("Enter your GitHub personal access token first.")
            return
        }
        status("Testing GitHub connection...")
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { GitHubDevClient(token).whoami() }
            }
            result.onSuccess { login ->
                findViewById<EditText>(R.id.etGhUser).setText(login)
                runCatching { preferences.githubUsername = login; preferences.githubToken = token }
                status("Connected as " + login + ". Token verified by GitHub.")
                loadRepos()
            }.onFailure { status(it.message ?: "GitHub test failed.") }
        }
    }

    private fun loadRepos() {
        val token = preferences.githubToken
        if (token.isBlank()) return
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { GitHubDevClient(token).listRepos() } }
            val list = findViewById<LinearLayout>(R.id.listRepos)
            list.removeAllViews()
            result.onSuccess { repos ->
                if (repos.isEmpty()) DevStudioUi.emptyNote(list, "No repositories found for this account yet.")
                repos.take(20).forEach { full ->
                    DevStudioUi.addRow(list, full, if (full == preferences.githubRepo) "Selected for pushes" else "Tap to select") {
                        preferences.githubRepo = full
                        findViewById<EditText>(R.id.etGhRepoName).setText(full)
                        status("Push target: " + full)
                        loadRepos()
                    }
                }
            }.onFailure { status(it.message ?: "Could not load repositories.") }
        }
    }

    private fun status(message: String) {
        findViewById<TextView>(R.id.tvGhStatus).text = message
    }
}
