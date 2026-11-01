package com.jarvis.assistant.lock

import android.content.Intent
import android.view.ViewGroup
import android.widget.LinearLayout
import com.jarvis.assistant.databinding.FragmentSettingsBinding

object LockSettingsRow {
    fun install(binding: FragmentSettingsBinding) {
        val anchor = binding.rowVoiceUnlock
        val parent = anchor.parent as? ViewGroup ?: return
        if (parent.findViewWithTag<android.view.View>("jarvisGlassLockRow") != null) return
        val row = MonoUi.button(anchor.context, "Liquid Glass Lock Screen") {
            anchor.context.startActivity(Intent(anchor.context, GlassLockSettingsActivity::class.java))
        }.apply { tag = "jarvisGlassLockRow" }
        parent.addView(row, parent.indexOfChild(anchor) + 1, LinearLayout.LayoutParams(-1, -2))
    }
}