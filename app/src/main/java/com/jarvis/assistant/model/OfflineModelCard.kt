package com.jarvis.assistant.model

import android.content.Context
import android.content.res.ColorStateList
import android.widget.LinearLayout
import android.widget.ProgressBar

class OfflineModelCard(context: Context, val modelId: String, title: String, private val detail: String,
    private val action: () -> Unit, private val pause: () -> Unit, remove: (() -> Unit)? = null) : LinearLayout(context) {
    private val note = OfflineModelStyle.text(context, detail, 12f)
    private val progress = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
        max = 100; progressTintList = ColorStateList.valueOf(OfflineModelStyle.lime); visibility = GONE
    }
    private var label = "Download & auto-setup"
    private var pausing = false
    private val button = OfflineModelStyle.button(context, label) { if (pausing) pause() else action() }
    private val deleteButton = remove?.let { OfflineModelStyle.button(context, "Delete from phone", it) }
    init {
        orientation = VERTICAL; OfflineModelStyle.panel(context, this)
        addView(OfflineModelStyle.text(context, title, 19f, true)); addView(note); addView(progress); addView(button)
        deleteButton?.let { addView(it) }
    }
    fun bind(state: ModelSetupViewModel.State, installed: Boolean, active: Boolean) {
        val selected = state.id == modelId
        pausing = selected && state.busy && state.phase == "download"
        button.isEnabled = !state.busy || pausing
        deleteButton?.isEnabled = !state.busy
        label = when {
            pausing -> "Pause download"
            selected && state.busy -> "Setting up…"
            active -> "Recheck setup"
            installed -> "Set up & activate"
            selected && state.phase == "paused" -> "Resume & auto-setup"
            selected && state.phase == "error" -> "Retry & auto-setup"
            else -> "Download & auto-setup"
        }
        button.text = label
        progress.visibility = if (selected && state.busy) VISIBLE else GONE
        progress.isIndeterminate = state.phase != "download" || state.total <= 0
        if (state.total > 0) progress.progress = (state.done * 100 / state.total).toInt()
        note.text = if (selected) state.message else if (active) "Active · setup previously tested on this phone" else detail
    }
}