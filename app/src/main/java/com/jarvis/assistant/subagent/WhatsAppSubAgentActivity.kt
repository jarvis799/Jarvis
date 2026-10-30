package com.jarvis.assistant.subagent

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.jarvis.assistant.R
import com.jarvis.assistant.automation.whatsapp.AutoReplyRule
import com.jarvis.assistant.automation.whatsapp.WhatsAppAutoReplyManager
import com.jarvis.assistant.notifications.JarvisNotificationListenerService
import com.jarvis.assistant.util.ThemeManager
import java.util.UUID

/**
 * DEV STYLE — WhatsApp sub agent.
 * Real controls for the notification-based auto reply engine: on/off switch,
 * matching rules, notification access and honest status. Replies are sent
 * through WhatsApp's own remote-input reply action, so nothing is typed into
 * the app and no account credentials are stored.
 */
class WhatsAppSubAgentActivity : AppCompatActivity() {

    private lateinit var ui: SubAgentUi
    private lateinit var manager: WhatsAppAutoReplyManager
    private lateinit var rulesContainer: LinearLayout
    private lateinit var statusCard: LinearLayout
    private lateinit var autoSwitch: SwitchCompat

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyTheme(this)
        super.onCreate(savedInstanceState)
        ui = SubAgentUi(this)
        manager = WhatsAppAutoReplyManager.get(this)
        setContentView(buildUi())
        refresh()
    }

    override fun onResume() {
        super.onResume()
        if (::rulesContainer.isInitialized) refresh()
    }

    private fun buildUi(): View {
        val root = FrameLayout(this)
        ThemeManager.applyDynamicBackground(root)

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(18), ui.dp(18), ui.dp(18), ui.dp(24))
        }
        column.addView(
            ui.header(
                titleText = "WhatsApp Agent",
                subtitleText = "Auto reply straight from the notification",
                onBack = { finish() }
            )
        )

        statusCard = ui.card().apply { setPadding(ui.dp(16), ui.dp(14), ui.dp(16), ui.dp(14)) }
        column.addView(statusCard, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = ui.dp(16) })

        // Master switch
        val switchCard = ui.card().apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
        }
        val switchColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            addView(ui.title("Auto reply"))
            addView(ui.hint("Answers new WhatsApp messages for you").apply { setPadding(0, ui.dp(3), 0, 0) })
        }
        autoSwitch = SwitchCompat(this).apply {
            contentDescription = "Turn WhatsApp auto reply on or off"
            setOnCheckedChangeListener { _, checked ->
                if (checked && manager.getRules().none { it.enabled && it.replyText.isNotBlank() }) {
                    Toast.makeText(this@WhatsAppSubAgentActivity, "Add one reply rule first", Toast.LENGTH_SHORT).show()
                    refresh()
                    showAddRuleDialog()
                    return@setOnCheckedChangeListener
                }
                manager.setEnabled(checked)
                Toast.makeText(
                    this@WhatsAppSubAgentActivity,
                    if (checked) "WhatsApp agent is on" else "WhatsApp agent paused",
                    Toast.LENGTH_SHORT
                ).show()
                refresh()
            }
        }
        switchCard.addView(switchColumn)
        switchCard.addView(autoSwitch)
        switchCard.setOnClickListener {
            if (!SubAgentStateResolver.notificationAccessGranted(this)) {
                askNotificationAccess()
            } else {
                autoSwitch.isChecked = !autoSwitch.isChecked
            }
        }
        column.addView(switchCard)

        // Actions
        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        actions.addView(
            ui.pillButton("Add reply rule").apply {
                setOnClickListener { showAddRuleDialog() }
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = ui.dp(10) }
        )
        actions.addView(
            ui.pillButton("Allow notification access", filled = false).apply {
                setOnClickListener { askNotificationAccess() }
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = ui.dp(10) }
        )
        actions.addView(
            ui.pillButton("Open WhatsApp", filled = false).apply {
                setOnClickListener {
                    val launch = packageManager.getLaunchIntentForPackage("com.whatsapp")
                    if (launch == null) {
                        Toast.makeText(this@WhatsAppSubAgentActivity, "WhatsApp is not installed", Toast.LENGTH_SHORT).show()
                    } else {
                        startActivity(launch)
                    }
                }
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
        column.addView(actions, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = ui.dp(4) })

        column.addView(ui.title("Reply rules", 15f).apply { setPadding(0, ui.dp(22), 0, ui.dp(8)) })
        column.addView(
            ui.hint(
                "Rule order does not matter: the most specific rule wins (contact + keyword beats a catch-all). " +
                    "Leave contact empty to answer everyone, or leave keyword empty to answer that contact always. " +
                    "Jarvis waits 60 seconds between replies to the same person so nobody gets spammed."
            )
        )

        rulesContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, ui.dp(14), 0, 0)
        }
        column.addView(rulesContainer)

        column.addView(
            ui.card().apply {
                setPadding(ui.dp(14), ui.dp(12), ui.dp(14), ui.dp(12))
                addView(ui.title("What this cannot do", 13f))
                addView(
                    ui.hint(
                        "It only answers notifications that carry WhatsApp's own reply action, and only while " +
                            "notification access is granted and WhatsApp notifications are enabled. " +
                            "Group chats answer only when a rule matches the sender. Nothing is sent when the " +
                            "phone is offline."
                    ).apply { setPadding(0, ui.dp(6), 0, 0) }
                )
            }.apply {
                (layoutParams as LinearLayout.LayoutParams).topMargin = ui.dp(18)
            }
        )

        root.addView(ScrollView(this).apply { addView(column) })
        return root
    }

    private fun refresh() {
        val rules = manager.getRules()
        val access = SubAgentStateResolver.notificationAccessGranted(this)
        val state = when {
            rules.none { it.enabled && it.replyText.isNotBlank() } -> "SETUP · no rule yet" to Color.parseColor("#D97706")
            !access -> "SETUP · notification access needed" to Color.parseColor("#D97706")
            manager.isEnabled() -> "ACTIVE · replying automatically" to Color.parseColor("#16A34A")
            else -> "PAUSED · switch is off" to ui.textMuted
        }

        statusCard.removeAllViews()
        statusCard.addView(ui.title(state.first, 14f).apply { setTextColor(state.second) })
        statusCard.addView(
            ui.hint(
                if (!access)
                    "Android has not given Jarvis notification access, so it cannot see the message or use the reply button."
                else "${rules.size} rule(s) saved · " + if (manager.isEnabled()) "auto reply on" else "auto reply off",
                11.5f
            ).apply { setPadding(0, ui.dp(6), 0, 0) }
        )

        autoSwitch.setOnCheckedChangeListener(null)
        autoSwitch.isChecked = manager.isEnabled()
        autoSwitch.setOnCheckedChangeListener { _, checked ->
            if (checked && rules.none { it.enabled && it.replyText.isNotBlank() }) {
                Toast.makeText(this, "Add one reply rule first", Toast.LENGTH_SHORT).show()
                refresh()
                showAddRuleDialog()
                return@setOnCheckedChangeListener
            }
            manager.setEnabled(checked)
            refresh()
        }

        rulesContainer.removeAllViews()
        if (rules.isEmpty()) {
            rulesContainer.addView(
                ui.card().apply {
                    addView(ui.title("No rule yet", 13.5f))
                    addView(ui.hint("Add a rule and Jarvis starts answering.").apply { setPadding(0, ui.dp(4), 0, 0) })
                }
            )
        } else {
            rules.forEach { rule -> rulesContainer.addView(ruleCard(rule)) }
        }
    }

    private fun ruleCard(rule: AutoReplyRule): LinearLayout = ui.card().apply {
        val top = LinearLayout(this@WhatsAppSubAgentActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val column = LinearLayout(this@WhatsAppSubAgentActivity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            addView(ui.title(if (rule.contactPattern.isBlank()) "Anyone" else rule.contactPattern, 13.5f))
            addView(
                ui.hint(
                    when {
                        rule.keywordPattern.isBlank() -> "Always replies"
                        else -> "When message contains: " + rule.keywordPattern
                    },
                    11f
                ).apply { setPadding(0, ui.dp(3), 0, 0) }
            )
            addView(ui.body("\"" + rule.replyText.take(120) + "\"", 12f).apply { setPadding(0, ui.dp(6), 0, 0) })
        }
        top.addView(column)
        top.addView(
            android.widget.TextView(this@WhatsAppSubAgentActivity).apply {
                text = "Remove"
                textSize = 12f
                setTextColor(Color.parseColor("#DC2626"))
                setPadding(ui.dp(10), ui.dp(8), ui.dp(4), ui.dp(8))
                isClickable = true
                setOnClickListener {
                    manager.removeRule(rule.id)
                    refresh()
                }
            }
        )
        addView(top)
    }

    private fun showAddRuleDialog() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(20), ui.dp(12), ui.dp(20), 0)
        }
        val contact = field("Contact name (empty = anyone)")
        val keyword = field("Keyword (empty = any message)")
        val reply = field("Reply text — {name} becomes the sender name", multiline = true)

        container.addView(contact)
        container.addView(keyword)
        container.addView(reply)
        container.addView(
            ui.hint(
                "Example: contact \"Ammu\", keyword empty, reply \"Bhai ekhon busy, pore call dibo.\"",
                11f
            ).apply { setPadding(0, ui.dp(8), 0, 0) }
        )

        AlertDialog.Builder(this)
            .setTitle("New reply rule")
            .setView(ScrollView(this).apply { addView(container) })
            .setPositiveButton("Save rule") { _, _ ->
                val replyText = reply.text.toString().trim()
                if (replyText.isBlank()) {
                    Toast.makeText(this, "Reply text is required", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                manager.addRule(
                    AutoReplyRule(
                        id = UUID.randomUUID().toString(),
                        contactPattern = contact.text.toString().trim(),
                        keywordPattern = keyword.text.toString().trim(),
                        replyText = replyText,
                        enabled = true
                    )
                )
                refresh()
                Toast.makeText(this, "Rule saved", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun field(hintText: String, multiline: Boolean = false): EditText = EditText(this).apply {
        hint = hintText
        textSize = 14f
        setTextColor(ui.textPrimary)
        setHintTextColor(ui.textMuted)
        background = ui.rounded(ui.alpha(ui.cardBg, 0.9f), 12, ui.alpha(ui.cardBorder, 0.8f))
        setPadding(ui.dp(12), ui.dp(10), ui.dp(12), ui.dp(10))
        inputType = if (multiline)
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        else InputType.TYPE_CLASS_TEXT
        if (multiline) minLines = 3
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = ui.dp(10) }
    }

    private fun askNotificationAccess() {
        AlertDialog.Builder(this)
            .setTitle("Notification access")
            .setMessage(
                "Android must allow Jarvis to read notifications before the WhatsApp agent can answer. " +
                    "Open the list, find Jarvis and switch it on."
            )
            .setPositiveButton("Open settings") { _, _ ->
                JarvisNotificationListenerService.openAccessSettings(this)
            }
            .setNegativeButton("Later", null)
            .show()
    }
}