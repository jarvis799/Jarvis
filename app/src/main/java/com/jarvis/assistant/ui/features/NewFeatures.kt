package com.jarvis.assistant.ui.features

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import com.google.android.material.dialog.MaterialAlertDialogBuilder

object NewFeatures {
    private const val TAG = "jarvisNewFeatures"

    fun install(anchor: View) {
        val parent = anchor.parent as? LinearLayout ?: return
        if (parent.findViewWithTag<View>(TAG) != null) return
        val panel = NewFeaturesView(anchor.context).apply { tag = TAG }
        parent.addView(panel, parent.indexOfChild(anchor), LinearLayout.LayoutParams(-1, -2))
    }

    fun show(context: Context) {
        MaterialAlertDialogBuilder(context)
            .setTitle("JARVIS Features")
            .setView(ScrollView(context).apply { addView(NewFeaturesView(context)) })
            .setPositiveButton("Close", null)
            .show()
    }
}
