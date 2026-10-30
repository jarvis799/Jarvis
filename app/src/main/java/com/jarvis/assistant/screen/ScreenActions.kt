package com.jarvis.assistant.screen

import android.content.Context
import android.speech.tts.TextToSpeech
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.jarvis.assistant.util.PermissionHelper
import java.util.Locale

/**
 * DEV STYLE: screen actions inside the existing Screen tools dialog - no separate screen.
 * Reads what is really on the current screen, speaks it, and taps an item the user picks.
 */
object ScreenActions {

    fun show(context: Context) {
        if (!PermissionHelper.isAccessibilityServiceEnabled(context)) {
            MaterialAlertDialogBuilder(context)
                .setTitle("Screen Actions")
                .setMessage("JARVIS reads the screen through Android Accessibility. Enable it, then open Screen tools again.")
                .setPositiveButton("Open Accessibility") { _, _ -> PermissionHelper.openAccessibilitySettings(context) }
                .setNegativeButton("Close", null)
                .show()
            return
        }
        val snapshot = ScreenSnapshotReader.capture()
        if (snapshot == null || snapshot.items.isEmpty()) {
            Toast.makeText(context, "Nothing readable on the current screen.", Toast.LENGTH_SHORT).show()
            return
        }
        val pad = (14 * context.resources.displayMetrics.density).toInt()
        val list = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        snapshot.items.take(40).forEach { item ->
            list.addView(TextView(context).apply {
                text = item.role + " • " + item.label
                textSize = 14f
                setPadding(pad, pad, pad, pad)
                isFocusable = true
                setOnClickListener {
                    if (tap(item)) {
                        Toast.makeText(context, "Tapped: " + item.label, Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "That item does not accept a tap.", Toast.LENGTH_SHORT).show()
                    }
                }
            })
        }
        MaterialAlertDialogBuilder(context)
            .setTitle(snapshot.items.size.toString() + " items on screen")
            .setView(ScrollView(context).apply { addView(list) })
            .setPositiveButton("Read aloud") { _, _ -> speak(context, snapshot.spokenText) }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun tap(item: ScreenItem): Boolean {
        var node: AccessibilityNodeInfo? = item.node
        var depth = 0
        while (node != null && depth < 6) {
            if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
            node = node.parent
            depth++
        }
        return false
    }

    private fun speak(context: Context, text: String) {
        val holder = arrayOfNulls<TextToSpeech>(1)
        holder[0] = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                holder[0]?.language = Locale.getDefault()
                holder[0]?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis-screen")
            }
        }
    }
}
