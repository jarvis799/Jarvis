package com.jarvis.assistant.ui.devstudio

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.jarvis.assistant.R
import com.jarvis.assistant.devstudio.DevStudioPreferences
import com.jarvis.assistant.devstudio.VercelClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Connect Vercel. Deployments still need an explicit approval in Project Studio. */
class VercelConnectActivity : AppCompatActivity() {

    private val preferences by lazy { DevStudioPreferences(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        com.jarvis.assistant.util.ThemeManager.applyTheme(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dev_vercel)
        com.jarvis.assistant.util.ThemeManager.applyDynamicBackground(findViewById(android.R.id.content))

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<EditText>(R.id.etVercelToken).setText(preferences.vercelToken)
        findViewById<EditText>(R.id.etVercelProject).setText(preferences.vercelProject)
        findViewById<View>(R.id.btnVercelTest).setOnClickListener { test() }
        findViewById<View>(R.id.btnVercelSave).setOnClickListener { save() }
        findViewById<View>(R.id.btnVercelDisconnect).setOnClickListener {
            preferences.disconnectVercel()
            findViewById<EditText>(R.id.etVercelToken).setText("")
            status("Vercel disconnected. The stored token was deleted.")
        }
    }

    private fun save() {
        val token = findViewById<EditText>(R.id.etVercelToken).text.toString().trim()
        if (token.isBlank()) {
            status("Enter your Vercel access token first.")
            return
        }
        runCatching {
            preferences.vercelToken = token
            preferences.vercelProject = findViewById<EditText>(R.id.etVercelProject).text.toString().trim()
        }.onFailure { status(it.message ?: "Could not save the token.") }.onSuccess {
            status("Saved in encrypted storage. Testing the connection...")
            test()
        }
    }

    private fun test() {
        val token = findViewById<EditText>(R.id.etVercelToken).text.toString().trim()
        if (token.isBlank()) {
            status("Enter your Vercel access token first.")
            return
        }
        status("Testing Vercel connection...")
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { VercelClient(token).whoami() } }
            result.onSuccess { account ->
                runCatching { preferences.vercelToken = token }
                status("Connected as " + account + ". Token verified by Vercel.")
            }.onFailure { status(it.message ?: "Vercel test failed.") }
        }
    }

    private fun status(message: String) {
        findViewById<TextView>(R.id.tvVercelStatus).text = message
    }
}
