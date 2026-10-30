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
        val connected = GmailStore.isConnected(context)
        view.text = if (connected) "GMAIL INBOX\nRead & reply • Hold for auto-reply settings" else "CONNECT GMAIL\n$state"
        view.setOnClickListener {
            val target = if (GmailStore.isConnected(context)) InboxActivity::class.java else GmailSettingsActivity::class.java
            context.startActivity(Intent(context, target))
        }
        view.setOnLongClickListener {
            context.startActivity(Intent(context, GmailSettingsActivity::class.java))
            true
        }
    }
}
