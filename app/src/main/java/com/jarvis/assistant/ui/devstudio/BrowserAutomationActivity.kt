package com.jarvis.assistant.ui.devstudio

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.view.View
import android.widget.EditText
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.jarvis.assistant.R
import com.jarvis.assistant.devstudio.BrowserAutomation
import com.jarvis.assistant.devstudio.DevStudioPreferences
import com.jarvis.assistant.devstudio.DevStudioRules
import kotlinx.coroutines.launch

/** Browser automation with an explicit approval gate and honest run evidence. */
class BrowserAutomationActivity : AppCompatActivity() {

    companion object { private const val REQ_VOICE = 4201 }

    private val preferences by lazy { DevStudioPreferences(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        com.jarvis.assistant.util.ThemeManager.applyTheme(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dev_browser)
        com.jarvis.assistant.util.ThemeManager.applyDynamicBackground(findViewById(android.R.id.content))

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        val switch = findViewById<Switch>(R.id.swBrowser)
        switch.isChecked = preferences.browserAutomationEnabled
        switch.setOnCheckedChangeListener { _, checked ->
            preferences.browserAutomationEnabled = checked
            append("Browser automation " + if (checked) "enabled." else "switched off.")
        }
        findViewById<View>(R.id.btnInstallRuntime).setOnClickListener { confirmInstall() }
        findViewById<View>(R.id.btnBrowserRun).setOnClickListener { requestRun() }
        findViewById<View>(R.id.btnBrowserVoice).setOnClickListener { listen() }
        refreshRuntime()
    }

    private fun refreshRuntime() {
        findViewById<TextView>(R.id.tvRuntime).text = "Checking the local runtime..."
        lifecycleScope.launch {
            val status = runCatching { BrowserAutomation.runtimeStatus() }.getOrDefault("Could not read the runtime status.")
            findViewById<TextView>(R.id.tvRuntime).text = status
        }
    }

    private fun confirmInstall() {
        AlertDialog.Builder(this)
            .setTitle("Install browser runtime")
            .setMessage("Installs Node.js, Chromium and the automation runtime inside the app's local Linux environment. About 400 MB and a network connection are needed.")
            .setPositiveButton("Install") { _, _ ->
                append("Installing. This can take a few minutes.")
                lifecycleScope.launch {
                    val ok = runCatching { BrowserAutomation.installRuntime { line -> append(line) } }.getOrDefault(false)
                    append(if (ok) "Runtime installed." else "Runtime installation did not finish.")
                    refreshRuntime()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun requestRun() {
        val command = findViewById<EditText>(R.id.etBrowserCommand).text.toString().trim()
        if (command.isBlank()) {
            DevStudioUi.toast(this, "Type what the browser should do first.")
            return
        }
        val refusal = DevStudioRules.bypassRefusal(command)
        if (refusal != null) {
            append(refusal)
            return
        }
        if (!BrowserAutomation.isEnabled()) {
            append("Switch browser automation on first.")
            return
        }
        val kind = DevStudioRules.classifyBrowserAction(command)
        if (!DevStudioRules.requiresApproval(kind)) {
            run(command, false)
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Approve this action")
            .setMessage(DevStudioRules.approvalPrompt(kind, "the target website") + " Only this one run is approved.")
            .setPositiveButton("Approve once") { _, _ -> run(command, true) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun run(command: String, approved: Boolean) {
        append("> " + command)
        lifecycleScope.launch {
            val result = runCatching { BrowserAutomation.runTask(command, approved) { line -> append(line) } }
            result.onSuccess { task ->
                append(task.evidence)
                if (task.output.isNotBlank()) append(task.output)
                if (task.ran && !task.verified) append("This run is NOT verified - treat the result as unconfirmed.")
            }.onFailure { append("Run failed: " + (it.message ?: "unknown error")) }
        }
    }

    private fun append(line: String) {
        if (line.isBlank()) return
        runOnUiThread {
            val output = findViewById<TextView>(R.id.tvBrowserOutput)
            output.text = (output.text.toString() + "\n" + line.trim()).trim().takeLast(6000)
        }
    }

    private fun listen() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PROMPT, "What should the browser do?")
        runCatching { startActivityForResult(intent, REQ_VOICE) }
            .onFailure { DevStudioUi.toast(this, "Voice input is not available on this device.") }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_VOICE && resultCode == Activity.RESULT_OK) {
            val spoken = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull().orEmpty()
            if (spoken.isNotBlank()) findViewById<EditText>(R.id.etBrowserCommand).setText(spoken)
        }
    }
}
