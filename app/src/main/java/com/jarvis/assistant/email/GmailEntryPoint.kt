package com.jarvis.assistant.email

import android.content.Intent
import android.widget.TextView

object GmailEntryPoint {
    fun bind(view: TextView) {
        val context = view.context
        val state = when {
            !GmailStore.isConnected(context) -> "Email + 16-character App Password — tap to set up"
            !GmailStore.connectionVerified(context) -> "Saved: ${GmailStore.email(context)} — tap to test"
            GmailStore.autoReplyEnabled(context) -> "${GmailStore.email(context)} — ON\nLast scan: ${GmailStore.scanStatus(context)}"
            else -> "${GmailStore.email(context)} — paused, tap to resume"
        }
        view.text = "GMAIL AUTO REPLY\n$state"
        view.setOnClickListener {
            context.startActivity(Intent(context, GmailSettingsActivity::class.java))
        }
    }
}
