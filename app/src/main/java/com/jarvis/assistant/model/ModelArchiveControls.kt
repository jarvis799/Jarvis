package com.jarvis.assistant.model

import android.content.Context
import android.widget.LinearLayout

class ModelArchiveControls(context: Context) : LinearLayout(context) {
    val folderButton = OfflineModelStyle.button(context, "Choose reusable model folder") {}
    val importButton = OfflineModelStyle.button(context, "Import saved GGUF · no download") {}
    val detail = OfflineModelStyle.text(context, "", 12f)
    init {
        orientation = VERTICAL
        addView(OfflineModelStyle.text(context, "Keep models after reinstall", 20f, true))
        addView(OfflineModelStyle.text(context,
            "Choose a folder in phone Documents/Downloads (or an SD card). Downloads are also saved there automatically. That file survives uninstall unless you delete it. After reinstall, tap Import and select it; setup runs again without downloading. Android removes the private runtime copy and folder permissions on uninstall.", 12f))
        addView(folderButton); addView(importButton); addView(detail)
    }
    fun refresh(busy: Boolean) {
        detail.text = if (ModelArchive(context).folder != null) "Reusable folder selected · keep it to avoid downloading again."
            else "Choose a folder before downloading. Existing files can be imported without a folder."
        folderButton.isEnabled = !busy; importButton.isEnabled = !busy
    }
}