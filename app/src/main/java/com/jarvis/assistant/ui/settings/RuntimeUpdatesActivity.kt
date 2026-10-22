package com.jarvis.assistant.ui.settings

import android.app.Activity
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.jarvis.assistant.runtime.FeatureInstaller
import com.jarvis.assistant.runtime.FeatureRegistry
import com.jarvis.assistant.runtime.FeatureRunner
import com.jarvis.assistant.runtime.FeatureViewerActivity
import com.jarvis.assistant.runtime.InstalledFeature
import com.jarvis.assistant.runtime.RuntimeUpdateManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Runtime Feature Updates - a single premium centered action. Tap Check
 * Updates: Jarvis scans GitHub Releases and, when newer feature packs exist,
 * downloads, verifies and installs them automatically - no repository or
 * token setup here. (The device owner's own GitHub token from Developer
 * Studio is used automatically when saved; public checks need none.)
 */
class RuntimeUpdatesActivity : Activity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var statusLine: TextView
    private lateinit var lastCheckedLine: TextView
    private lateinit var checkButton: TextView
    private lateinit var listHost: LinearLayout
    private var busy = false

    private val cyan = 0xFF4D7C0F.toInt()
    private val purple = 0xFF65A30D.toInt()
    private val ink = 0xFF12250A.toInt()
    private val grey = 0xFF4C6436.toInt()
    private val red = 0xFFF87171.toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildUi())
        renderInstalled()
        if (RuntimeUpdateManager.lastChecked(this) == 0L) {
            statusLine.text = "Tap Check Updates to look for new features."
            lastCheckedLine.text = "Never checked"
        } else {
            lastCheckedLine.text = "Last checked " + time(RuntimeUpdateManager.lastChecked(this))
            statusLine.text = "Tap Check Updates to look for new features."
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun buildUi(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFFF3FBEA.toInt())
        }
        val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(dp(10), dp(14), dp(18), dp(4)) }
        bar.addView(TextView(this).apply {
            text = "<"
            textSize = 24f; typeface = Typeface.DEFAULT_BOLD; setTextColor(cyan)
            setPadding(dp(10), 0, dp(16), 0)
            setOnClickListener { finish() }
        })
        bar.addView(TextView(this).apply {
            text = "RUNTIME FEATURE UPDATES"
            textSize = 18f; typeface = Typeface.DEFAULT_BOLD; letterSpacing = 0.08f
            setTextColor(ink); layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        root.addView(bar)

        val scroll = ScrollView(this)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(26), dp(18), dp(26), dp(30))
        }
        col.addView(TextView(this).apply {
            text = "RUNTIME FEATURE UPDATES"
            textSize = 24f; typeface = Typeface.DEFAULT_BOLD; letterSpacing = 0.12f
            setTextColor(ink); gravity = Gravity.CENTER
        })

        checkButton = TextView(this).apply {
            text = "Check Updates"
            textSize = 16f; typeface = Typeface.DEFAULT_BOLD; setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
            background = GradientDrawable().apply { setColor(purple); cornerRadius = dp(20).toFloat() }
            setPadding(dp(44), dp(15), dp(44), dp(15))
            setOnClickListener { onCheck() }
        }
        val buttonWrap = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
            setPadding(0, dp(30), 0, dp(8))
        }
        buttonWrap.addView(checkButton)
        col.addView(buttonWrap)

        lastCheckedLine = TextView(this).apply {
            textSize = 11f; setTextColor(grey); gravity = Gravity.CENTER
        }
        col.addView(lastCheckedLine)

        statusLine = TextView(this).apply {
            textSize = 13f; setTextColor(cyan); gravity = Gravity.CENTER
            setPadding(dp(8), dp(14), dp(8), 0)
        }
        col.addView(statusLine)

        listHost = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(24), 0, 0)
        }
        col.addView(listHost)

        scroll.addView(col)
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        return root
    }

    private fun status(text: String, color: Int = cyan) {
        runOnUiThread {
            statusLine.text = text
            statusLine.setTextColor(color)
        }
    }

    private fun time(ms: Long): String =
        SimpleDateFormat("d MMM, h:mm a", Locale.US).format(Date(ms))

    private fun onCheck() {
        if (busy) return
        busy = true
        checkButton.alpha = 0.45f
        status("Checking GitHub Releases for updates...")
        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) { RuntimeUpdateManager.check(applicationContext) }
                if (result.available.isEmpty()) {
                    status(if (result.requiresAppUpdate.isEmpty())
                        "You are up to date - no new features right now."
                    else
                        "You are up to date. " + result.requiresAppUpdate.size + " feature(s) need an app update first.")
                } else {
                    val installed = mutableListOf<String>()
                    result.available.forEachIndexed { index, feature ->
                        status("Installing " + feature.name + " (" + (index + 1) + "/" + result.available.size + ")...")
                        val zip = withContext(Dispatchers.IO) {
                            FeatureInstaller.download(applicationContext, feature) { percent ->
                                status("Downloading " + feature.name + " - " + percent + "%")
                            }
                        }
                        val installedFeature = withContext(Dispatchers.IO) {
                            FeatureInstaller.install(applicationContext, zip, feature)
                        }
                        zip.delete()
                        withContext(Dispatchers.IO) { FeatureRunner.activate(applicationContext, installedFeature) }
                        withContext(Dispatchers.IO) { FeatureInstaller.confirmActivation(applicationContext, installedFeature.id) }
                        installed.add(feature.name)
                    }
                    var message = "Installed " + installed.size + " feature" +
                        (if (installed.size == 1) "" else "s") + ": " + installed.joinToString(", ") + ". Everything is active."
                    if (result.requiresAppUpdate.isNotEmpty())
                        message += " " + result.requiresAppUpdate.size + " more feature(s) need an app update first."
                    status(message)
                }
                runOnUiThread {
                    lastCheckedLine.text = "Last checked " + time(System.currentTimeMillis())
                    renderInstalled()
                }
            } catch (e: Exception) {
                status(e.message ?: "Update check failed. Check your connection and try again.", red)
            } finally {
                busy = false
                runOnUiThread { checkButton.alpha = 1f }
            }
        }
    }

    private fun renderInstalled() {
        runOnUiThread {
            listHost.removeAllViews()
            val installed = FeatureRegistry.all(this)
            if (installed.isEmpty()) return@runOnUiThread
            listHost.addView(TextView(this).apply {
                text = "INSTALLED FEATURES"
                textSize = 11f; typeface = Typeface.DEFAULT_BOLD; letterSpacing = 0.1f
                setTextColor(grey)
                setPadding(dp(8), dp(6), dp(8), dp(6))
            })
            for (f in installed) addRow(f)
        }
    }

    private fun addRow(f: InstalledFeature) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply { setColor(0xFFEAFFE0.toInt()); cornerRadius = dp(14).toFloat() }
            setPadding(dp(16), dp(12), dp(16), dp(12))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(8)
            }
        }
        val label = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) }
        label.addView(TextView(this).apply { text = f.name; textSize = 14f; typeface = Typeface.DEFAULT_BOLD; setTextColor(ink) })
        label.addView(TextView(this).apply { text = f.version + " - " + f.featureType.replace("_", " "); textSize = 11f; setTextColor(grey); setPadding(0, dp(2), 0, 0) })
        row.addView(label)
        if (f.featureType == "web_tool") {
            row.addView(TextView(this).apply {
                text = "Open"
                textSize = 12f; typeface = Typeface.DEFAULT_BOLD; setTextColor(cyan)
                setPadding(dp(14), dp(6), dp(6), dp(6))
                setOnClickListener {
                    startActivity(android.content.Intent(this@RuntimeUpdatesActivity, FeatureViewerActivity::class.java)
                        .putExtra("feature_id", f.id))
                }
            })
        }
        listHost.addView(row)
    }
}
