package com.jarvis.assistant.email

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.jarvis.assistant.util.ThemeManager
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * DEV STYLE: real inbox over the existing IMAP client.
 * Lists the latest messages, opens the full body, and sends a reply through SMTP.
 */
class InboxActivity : AppCompatActivity() {

    private lateinit var listContainer: LinearLayout
    private lateinit var statusView: TextView
    private val dateFormat = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyTheme(this)
        super.onCreate(savedInstanceState)
        buildUi()
        load()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }
        root.addView(TextView(this).apply { text = "Inbox"; textSize = 22f })

        statusView = TextView(this).apply {
            text = "Loading…"
            textSize = 12f
            setPadding(0, 6, 0, 12)
        }
        root.addView(statusView)

        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actions.addView(Button(this).apply {
            text = "Refresh"
            setOnClickListener { load() }
        })
        actions.addView(Button(this).apply {
            text = "Gmail Settings"
            setOnClickListener {
                startActivity(Intent(this@InboxActivity, GmailSettingsActivity::class.java))
            }
        })
        root.addView(actions)

        listContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            addView(listContainer)
        })
        setContentView(root)
    }

    private fun load() {
        listContainer.removeAllViews()
        if (!GmailStore.isConnected(this)) {
            statusView.text = "Gmail is not connected yet. Save your address and 16-character App Password in Gmail Settings."
            return
        }
        statusView.text = "Loading latest mail…"
        lifecycleScope.launch {
            val result = runCatching { GmailClient.fetchLatest(this@InboxActivity, 20) }
            if (isFinishing || isDestroyed) return@launch
            listContainer.removeAllViews()
            result.onSuccess { messages ->
                statusView.text =
                    if (messages.isEmpty()) "No mail found." else "${messages.size} latest messages"
                messages.forEach { listContainer.addView(messageRow(it)) }
            }.onFailure {
                statusView.text = "Could not load mail: ${it.message}"
            }
        }
    }

    private fun messageRow(message: GmailMessage): TextView = TextView(this).apply {
        text = buildString {
            append(message.fromName.ifBlank { message.fromEmail })
            append("\n")
            append(message.subject.ifBlank { "(no subject)" })
            append("\n")
            append(dateFormat.format(Date(message.sentTime)))
        }
        textSize = 14f
        setPadding(24, 22, 24, 22)
        setOnClickListener { openMessage(message) }
    }

    private fun openMessage(message: GmailMessage) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 8)
        }
        container.addView(TextView(this).apply {
            text = message.subject.ifBlank { "(no subject)" }
            textSize = 17f
            setPadding(0, 0, 0, 6)
        })
        container.addView(TextView(this).apply {
            text = "${message.fromName} <${message.fromEmail}>"
            textSize = 12f
            setPadding(0, 0, 0, 12)
        })
        container.addView(TextView(this).apply {
            text = message.body.take(6000)
            textSize = 13f
        })
        container.addView(TextView(this).apply {
            text = "Reply"
            textSize = 12f
            setPadding(0, 16, 0, 4)
        })
        val reply = EditText(this).apply {
            hint = "Type your reply…"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 2
        }
        container.addView(reply)

        MaterialAlertDialogBuilder(this)
            .setTitle("Message")
            .setView(ScrollView(this).apply { addView(container) })
            .setPositiveButton("Send reply") { _, _ ->
                val text = reply.text.toString().trim()
                if (text.isEmpty()) {
                    Toast.makeText(this, "Reply is empty.", Toast.LENGTH_SHORT).show()
                } else {
                    sendReply(message, text)
                }
            }
            .setNeutralButton("Mark read") { _, _ -> markRead(message) }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun sendReply(message: GmailMessage, text: String) {
        lifecycleScope.launch {
            val subject =
                if (message.subject.startsWith("Re:", true)) message.subject
                else "Re: ${message.subject}"
            val result = runCatching {
                GmailClient.send(
                    this@InboxActivity,
                    message.fromEmail,
                    subject,
                    text,
                    message.messageId
                )
            }
            if (isFinishing || isDestroyed) return@launch
            val notice = result.fold(
                { "Reply sent to ${message.fromEmail}" },
                { "Send failed: ${it.message}" }
            )
            Toast.makeText(this@InboxActivity, notice, Toast.LENGTH_LONG).show()
        }
    }

    private fun markRead(message: GmailMessage) {
        lifecycleScope.launch {
            val result = runCatching { GmailClient.markSeen(this@InboxActivity, message.uid) }
            if (isFinishing || isDestroyed) return@launch
            Toast.makeText(
                this@InboxActivity,
                result.fold({ "Marked as read." }, { "Could not mark as read: ${it.message}" }),
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}