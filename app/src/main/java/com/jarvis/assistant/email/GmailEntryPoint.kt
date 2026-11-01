package com.jarvis.assistant.email

import android.content.Intent
import android.widget.TextView

object GmailEntryPoint {
    fun bind(view: TextView) {
        val context = view.context
        val state = when {
            !GmailStore.isConnected(context) -> "Email + 16-character App Password — tap to set up"
            !GmailStore.connectionVerified(context) -> "Saved account — tap to test the connection"
            GmailStore.autoReplyEnabled(context) -> "Background auto reply ON — tap to manage"
            else -> "Auto reply paused — tap to resume"
        }
        view.text = "GMAIL AUTO REPLY\n$state"
        view.setOnClickListener {
            context.startActivity(Intent(context, GmailSettingsActivity::class.java))
        }
    }
}
