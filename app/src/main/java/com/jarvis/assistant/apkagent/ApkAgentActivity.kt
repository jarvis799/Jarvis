package com.jarvis.assistant.apkagent

import androidx.activity.ComponentActivity

import android.annotation.SuppressLint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * DEV STYLE: APK Agent dashboard - import an APK, get a truthful analysis
 * report, then plan changes one question at a time with explicit approval.
 */
class ApkAgentActivity : ComponentActivity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var reportView: TextView
    private lateinit var chatLabel: TextView
    private lateinit var chatInput: EditText
    private lateinit var sendButton: Button
    private lateinit var statusLine: TextView

    private val pickApk = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) handlePicked(uri) else statusLine.setText("Nothing selected.")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
    }

    @SuppressLint("SetTextI18n")
    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF070B14.toInt())
            setPadding(dp(20), dp(22), dp(20), dp(20))
        }
        root.addView(TextView(this).apply {
            text = "APK AGENT"
            textSize = 25f
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.12f
            setTextColor(0xFFF4F7FF.toInt())
        })
        root.addView(TextView(this).apply {
            text = "Understand and modify APKs - truthful analysis, one question at a time"
            textSize = 12f
            setTextColor(0xFF8FA3C8.toInt())
            setPadding(0, dp(4), 0, 0)
        })
        statusLine = TextView(this).apply {
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(0xFF22D3EE.toInt())
            setPadding(0, dp(10), 0, dp(6))
        }
        root.addView(statusLine)

        root.addView(pillButton("ANALYZE APK (PICK A FILE)", 0xFF6C5CE7.toInt()) {
            pickApk.launch(arrayOf("*/*"))
        })
        root.addView(pillButton("SKILL REGISTRY", 0xFF1C2436.toInt()) { showSkills() })

        reportView = TextView(this).apply {
            textSize = 11.5f
            typeface = Typeface.MONOSPACE
            setTextColor(0xFFCBD5F0.toInt())
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = GradientDrawable().apply { setColor(0xFF101828.toInt()); cornerRadius = dp(14f).toFloat() }
            visibility = View.GONE
        }
        val reportScroll = ScrollView(this)
        reportScroll.addView(reportView)
        root.addView(reportScroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply { topMargin = dp(12) })

        chatLabel = TextView(this).apply {
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(0xFFCBD5F0.toInt())
            setPadding(0, dp(10), 0, dp(6))
        }
        root.addView(chatLabel)
        chatInput = EditText(this).apply {
            hint = "Your answer / request"
            setTextColor(0xFFF4F7FF.toInt())
            setHintTextColor(0xFF5B6B8C.toInt())
            textSize = 12.5f
            background = GradientDrawable().apply {
                setColor(0xFF101828.toInt()); cornerRadius = dp(12f).toFloat()
                setStroke(dp(1), 0xFF24304A.toInt())
            }
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        root.addView(chatInput)
        sendButton = pillButton("SEND", 0xFF22D3EE.toInt()) { onSend() }
        (sendButton.layoutParams as LinearLayout.LayoutParams).topMargin = dp(10)
        root.addView(sendButton)
        setContentView(root)
    }

    private fun handlePicked(uri: Uri) {
        val name = uri.lastPathSegment ?: "selected file"
        if (!name.endsWith(".apk", true)) {
            statusLine.text = "That does not look like an APK file (name: " + name + "). Pick a .apk file."
            return
        }
        statusLine.text = "Importing and analyzing..."
        scope.launch {
            val reportText = withContext(Dispatchers.IO) {
                val imported = ApkImportManager.import(this@ApkAgentActivity, uri)
                val report = ApkMetadataAnalyzer.analyze(
                    this@ApkAgentActivity, imported.displayName ?: "reference.apk",
                    imported.sha256, imported.sizeBytes, imported.file
                )
                SkillRegistry.recordExecution(this@ApkAgentActivity, "apk-metadata-analysis", "Analyzed " + report.packageName)
                ApkMetadataAnalyzer.toText(report)
            }
            reportView.text = reportText
            reportView.visibility = View.VISIBLE
            statusLine.text = "Analysis complete."
            chatLabel.text = ApkChangePlanner.startWithReport(reportText)
        }
    }

    private fun onSend() {
        val text = chatInput.text.toString().trim()
        if (text.isEmpty()) return
        chatInput.setText("")
        val reply = ApkChangePlanner.answer(text)
        chatLabel.text = reply
    }

    private fun showSkills() {
        val sb = StringBuilder("SKILL REGISTRY\n\n")
        for (s in SkillRegistry.list()) {
            sb.append("- ").append(s.name).append(" [").append(s.status).append(" / risk ").append(s.risk).append("]\n")
            sb.append("  ").append(s.description).append("\n")
            sb.append("  Needs: ").append(s.dependencies).append("\n")
            if (s.notes.isNotBlank()) sb.append("  Note: ").append(s.notes).append("\n")
            sb.append("\n")
        }
        reportView.text = sb.toString()
        reportView.visibility = View.VISIBLE
        statusLine.text = "Skills listed - external libraries are reviewed, never auto-executed."
    }

    private fun pillButton(label: String, color: Int, onClick: () -> Unit): Button = Button(this).apply {
        text = label
        isAllCaps = true
        textSize = 12.5f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(0xFFFFFFFF.toInt())
        background = GradientDrawable().apply { setColor(color); cornerRadius = dp(999f).toFloat() }
        setPadding(dp(18), dp(10), dp(18), dp(10))
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
    private fun dp(v: Float): Int = (v * resources.displayMetrics.density).toInt()
}
