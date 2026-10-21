package com.jarvis.assistant.runtime.update

import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RuntimeUpdateCenterActivity : AppCompatActivity() {

    private lateinit var vm: RuntimeUpdateViewModel
    private lateinit var statusLine: TextView
    private lateinit var versionLine: TextView
    private lateinit var lastCheckedLine: TextView
    private lateinit var checkButton: TextView
    private lateinit var card: LinearLayout
    private lateinit var cardTitle: TextView
    private lateinit var cardBody: TextView
    private lateinit var cardMeta: TextView
    private lateinit var installButton: TextView
    private lateinit var cancelButton: TextView
    private lateinit var progress: ProgressBar
    private lateinit var progressLabel: TextView
    private lateinit var listHost: LinearLayout

    private val cyan = 0xFF48CEDA.toInt()
    private val purple = 0xFF7E63F5.toInt()
    private val ink = 0xFFF4F7FF.toInt()
    private val grey = 0xFF8FA3C8.toInt()
    private val green = 0xFF65A30D.toInt()
    private val red = 0xFFF87171.toInt()

    private var pendingManifest: UpdateManifest? = null

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        vm = ViewModelProvider(this)[RuntimeUpdateViewModel::class.java]
        setContentView(buildUi())
        renderInstalled()
        lastCheckedLine.text = if (ModuleStore.lastChecked(this) == 0L) "Never checked"
            else "Last checked " + time(ModuleStore.lastChecked(this))

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) { vm.state.collect { render(it) } }
        }
    }

    private fun buildUi(): View {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(0xFF0A0C10.toInt()) }
        val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(dp(10), dp(14), dp(18), dp(4)) }
        bar.addView(TextView(this).apply {
            text = "<"; textSize = 24f; typeface = Typeface.DEFAULT_BOLD; setTextColor(cyan)
            setPadding(dp(10), 0, dp(16), 0); setOnClickListener { finish() }
        })
        bar.addView(TextView(this).apply {
            text = "RUNTIME UPDATE CENTER"; textSize = 18f; typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.08f; setTextColor(ink)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        root.addView(bar)

        val scroll = ScrollView(this)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(26), dp(18), dp(26), dp(30))
        }
        col.addView(TextView(this).apply {
            text = "RUNTIME UPDATE CENTER"; textSize = 22f; typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.12f; setTextColor(ink); gravity = Gravity.CENTER
        })
        versionLine = TextView(this).apply {
            textSize = 12f; setTextColor(grey); gravity = Gravity.CENTER; setPadding(0, dp(8), 0, 0)
        }
        col.addView(versionLine)
        lastCheckedLine = TextView(this).apply { textSize = 11f; setTextColor(grey); gravity = Gravity.CENTER; setPadding(0, dp(2), 0, dp(4)) }
        col.addView(lastCheckedLine)

        checkButton = TextView(this).apply {
            text = "Check for Updates"; textSize = 16f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(0xFFFFFFFF.toInt()); gravity = Gravity.CENTER
            background = GradientDrawable().apply { setColor(purple); cornerRadius = dp(20).toFloat() }
            setPadding(dp(44), dp(15), dp(44), dp(15)); setOnClickListener { vm.check() }
        }
        col.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; setPadding(0, dp(24), 0, dp(4))
            addView(checkButton)
        })

        statusLine = TextView(this).apply { textSize = 13f; setTextColor(cyan); gravity = Gravity.CENTER; setPadding(dp(8), dp(10), dp(8), 0) }
        col.addView(statusLine)

        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100; progress = 0; visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(8)).apply { topMargin = dp(16) }
        }
        col.addView(progress)
        progressLabel = TextView(this).apply { textSize = 11f; setTextColor(grey); gravity = Gravity.CENTER; visibility = View.GONE }
        col.addView(progressLabel)

        card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; visibility = View.GONE
            background = GradientDrawable().apply { setColor(0xFF12161D.toInt()); cornerRadius = dp(16).toFloat() }
            setPadding(dp(18), dp(16), dp(18), dp(16))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(18) }
        }
        cardTitle = TextView(this).apply { textSize = 16f; typeface = Typeface.DEFAULT_BOLD; setTextColor(ink) }
        card.addView(cardTitle)
        cardMeta = TextView(this).apply { textSize = 11f; setTextColor(grey); setPadding(0, dp(2), 0, 0) }
        card.addView(cardMeta)
        cardBody = TextView(this).apply { textSize = 12f; setTextColor(grey); setPadding(0, dp(8), 0, 0) }
        card.addView(cardBody)
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; setPadding(0, dp(14), 0, 0) }
        installButton = TextView(this).apply {
            text = "Install Update"; textSize = 14f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(0xFF121212.toInt()); gravity = Gravity.CENTER
            background = GradientDrawable().apply { setColor(0xFFA3E635.toInt()); cornerRadius = dp(18).toFloat() }
            setPadding(dp(28), dp(11), dp(28), dp(11)); setOnClickListener { pendingManifest?.let { vm.apply(it) } }
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(10) }
        }
        cancelButton = TextView(this).apply {
            text = "Cancel"; textSize = 14f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(ink); gravity = Gravity.CENTER; visibility = View.GONE
            background = GradientDrawable().apply { setColor(0xFF2A2F3A.toInt()); cornerRadius = dp(18).toFloat() }
            setPadding(dp(24), dp(11), dp(24), dp(11)); setOnClickListener { vm.cancel() }
        }
        actions.addView(installButton)
        actions.addView(cancelButton)
        card.addView(actions)
        col.addView(card)

        listHost = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(24), 0, 0) }
        col.addView(listHost)

        scroll.addView(col)
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        return root
    }

    private fun render(state: RuntimeUpdateViewModel.UiState) {
        versionLine.text = "Current runtime: " + vm.currentRuntimeVersion
        when (state) {
            is RuntimeUpdateViewModel.UiState.Idle -> { setStatus("Ready. Tap Check for Updates.", cyan) }
            is RuntimeUpdateViewModel.UiState.Disabled -> { setStatus(state.message, grey); hideCard() }
            is RuntimeUpdateViewModel.UiState.Checking -> {
                setStatus("Checking the update server...", cyan); hideCard()
                ModuleStore.setLastChecked(this, System.currentTimeMillis())
                lastCheckedLine.text = "Last checked " + time(System.currentTimeMillis())
            }
            is RuntimeUpdateViewModel.UiState.UpToDate -> { setStatus("You are up to date.", green); hideCard() }
            is RuntimeUpdateViewModel.UiState.Available -> {
                pendingManifest = state.manifest
                setStatus("A signed update is available.", cyan)
                showCard(state.manifest.runtimeVersion, state.moduleCount, state.totalSize, state.manifest.releaseNotes)
                progress.visibility = View.GONE; progressLabel.visibility = View.GONE
                installButton.visibility = View.VISIBLE; cancelButton.visibility = View.GONE
            }
            is RuntimeUpdateViewModel.UiState.Downloading -> {
                setStatus("Downloading runtime " + state.runtimeVersion + "...", cyan)
                progress.visibility = View.VISIBLE; progressLabel.visibility = View.VISIBLE
                progress.progress = state.percent
                progressLabel.text = state.moduleName + " - " + state.moduleIndex + "/" + state.moduleCount + " - " + state.percent + "%"
                installButton.visibility = View.GONE; cancelButton.visibility = View.VISIBLE
            }
            is RuntimeUpdateViewModel.UiState.Installing -> {
                setStatus("Activating " + state.moduleIndex + "/" + state.moduleCount + "...", cyan)
                progress.visibility = View.GONE; progressLabel.visibility = View.GONE
                installButton.visibility = View.GONE; cancelButton.visibility = View.GONE
            }
            is RuntimeUpdateViewModel.UiState.Applied -> {
                setStatus("Runtime " + state.runtimeVersion + " is active. Updated: " + state.modules.joinToString(", "), green)
                hideCard(); progress.visibility = View.GONE; progressLabel.visibility = View.GONE
                renderInstalled()
            }
            is RuntimeUpdateViewModel.UiState.RequiresAppUpdate -> { setStatus(state.reason, red); hideCard() }
            is RuntimeUpdateViewModel.UiState.Error -> {
                setStatus(state.message, red); hideCard()
                progress.visibility = View.GONE; progressLabel.visibility = View.GONE
                installButton.visibility = View.VISIBLE; cancelButton.visibility = View.GONE
            }
        }
    }

    private fun showCard(version: String, modules: Int, totalSize: Long, notes: String) {
        card.visibility = View.VISIBLE
        cardTitle.text = "Runtime " + version
        cardMeta.text = modules.toString() + " module" + (if (modules == 1) "" else "s") + " - " + (totalSize / (1024 * 1024)) + " MB - signed"
        cardBody.text = notes.ifBlank { "No release notes." }
    }
    private fun hideCard() { card.visibility = View.GONE }
    private fun setStatus(text: String, color: Int) { statusLine.text = text; statusLine.setTextColor(color) }

    private fun renderInstalled() {
        listHost.removeAllViews()
        val installed = ModuleStore.modules(this).filter { it.active }
        if (installed.isEmpty()) return
        listHost.addView(TextView(this).apply {
            text = "ACTIVE MODULES"; textSize = 11f; typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.1f; setTextColor(grey); setPadding(dp(8), dp(6), dp(8), dp(6))
        })
        for (m in installed) addModuleRow(m)
    }

    private fun addModuleRow(m: ModuleStore.InstalledModule) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply { setColor(0xFF12161D.toInt()); cornerRadius = dp(14).toFloat() }
            setPadding(dp(16), dp(12), dp(16), dp(12))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(8) }
        }
        val label = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) }
        label.addView(TextView(this).apply { text = m.name; textSize = 14f; typeface = Typeface.DEFAULT_BOLD; setTextColor(ink) })
        label.addView(TextView(this).apply { text = m.version + " - " + m.type.replace("_", " "); textSize = 11f; setTextColor(grey); setPadding(0, dp(2), 0, 0) })
        row.addView(label)
        if (ModuleStore.previousVersion(this, m.id) != null) {
            row.addView(TextView(this).apply {
                text = "Rollback"; textSize = 12f; typeface = Typeface.DEFAULT_BOLD; setTextColor(ink)
                background = GradientDrawable().apply { setColor(0xFF2A2F3A.toInt()); cornerRadius = dp(14).toFloat() }
                setPadding(dp(16), dp(8), dp(16), dp(8)); setOnClickListener { vm.rollback(m.id) }
            })
        }
        listHost.addView(row)
    }

    private fun time(ms: Long): String = SimpleDateFormat("d MMM, h:mm a", Locale.US).format(Date(ms))
}
