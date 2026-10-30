package com.jarvis.assistant.screen

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.jarvis.assistant.automation.accessibility.JarvisAccessibilityService
import com.jarvis.assistant.util.PermissionHelper
import com.jarvis.assistant.util.ThemeManager
import java.util.Locale

/** DEV STYLE: read the current screen and act on what is really there. */
class ScreenActionsActivity : AppCompatActivity() {

    private lateinit var statusView: TextView
    private lateinit var listContainer: LinearLayout
    private var speech: TextToSpeech? = null
    private var speechReady = false
    private var snapshot: ScreenSnapshot? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyTheme(this)
        super.onCreate(savedInstanceState)
        buildUi()
        speech = TextToSpeech(this) { status ->
            speechReady = status == TextToSpeech.SUCCESS
            if (speechReady) speech?.language = Locale.getDefault()
        }
        refresh()
    }

    private fun buildUi() {
        val container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 24, 24, 16) }
        container.addView(TextView(this).apply { text = "Screen Actions"; textSize = 20f; setPadding(0, 0, 0, 6) })
        statusView = TextView(this).apply { textSize = 12f; setPadding(0, 0, 0, 12) }
        container.addView(statusView)
        listContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        container.addView(listContainer)

        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(16, 8, 16, 16) }
        actions.addView(Button(this).apply { text = "Refresh"; setOnClickListener { refresh() } })
        actions.addView(Button(this).apply { text = "Read aloud"; setOnClickListener { readAloud() } })
        actions.addView(Button(this).apply { text = "Back"; setOnClickListener { global(AccessibilityService.GLOBAL_ACTION_BACK) } })

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(ScrollView(this).apply {
            addView(container)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        })
        root.addView(actions)
        setContentView(root)
        ThemeManager.applyDynamicBackground(root)
    }

    private fun refresh() {
        listContainer.removeAllViews()
        snapshot = null
        if (!PermissionHelper.isAccessibilityServiceEnabled(this)) {
            statusView.text = "Screen reading needs JARVIS in Android Accessibility. Enable it, then tap Refresh."
            listContainer.addView(Button(this).apply {
                text = "Open Accessibility Settings"
                setOnClickListener { PermissionHelper.openAccessibilitySettings(this@ScreenActionsActivity) }
            })
            return
        }
        val current = ScreenSnapshotReader.capture()
        if (current == null) {
            statusView.text = "The screen could not be read. Open another app and tap Refresh."
            return
        }
        snapshot = current
        statusView.text = "On screen: " + current.packageName + " - " + current.items.size + " readable items - tap one to act"
        current.items.take(80).forEach { item ->
            listContainer.addView(TextView(this).apply {
                text = item.role + " - " + item.label
                textSize = 14f
                setPadding(0, 18, 0, 18)
                isFocusable = true
                setOnClickListener { activate(item) }
            })
        }
    }

    private fun activate(item: ScreenItem) {
        var node: AccessibilityNodeInfo? = item.node
        var depth = 0
        while (node != null && depth < 6) {
            if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return
            node = node.parent
            depth++
        }
        Toast.makeText(this, "That item does not accept a tap.", Toast.LENGTH_SHORT).show()
    }

    private fun readAloud() {
        val current = snapshot ?: ScreenSnapshotReader.capture()
        if (current == null || current.items.isEmpty()) {
            Toast.makeText(this, "Nothing readable on screen yet.", Toast.LENGTH_SHORT).show()
            return
        }
        snapshot = current
        if (!speechReady) {
            Toast.makeText(this, "The phone speech engine is not ready.", Toast.LENGTH_SHORT).show()
            return
        }
        speech?.speak(current.spokenText, TextToSpeech.QUEUE_FLUSH, null, "jarvis-screen")
    }

    private fun global(action: Int) {
        val service = JarvisAccessibilityService.instance
        if (service == null) {
            Toast.makeText(this, "Enable JARVIS Accessibility first.", Toast.LENGTH_SHORT).show()
            return
        }
        service.performGlobalAction(action)
        statusView.postDelayed({ refresh() }, 600L)
    }

    override fun onDestroy() {
        speech?.stop()
        speech?.shutdown()
        speech = null
        super.onDestroy()
    }
}
