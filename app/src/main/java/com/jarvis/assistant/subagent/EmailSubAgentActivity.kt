package com.jarvis.assistant.subagent

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.webkit.WebView
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.jarvis.assistant.R
import com.jarvis.assistant.email.GmailSettingsActivity
import com.jarvis.assistant.email.GmailStore
import com.jarvis.assistant.email.HtmlReplyTemplate
import com.jarvis.assistant.util.ThemeManager

/**
 * DEV STYLE — Email sub agent.
 * Gmail 16-character App Password connection, the auto reply switch, and the
 * HTML reply designer with a live, JavaScript-disabled preview. Every reply the
 * background worker sends is the design saved here.
 */
class EmailSubAgentActivity : AppCompatActivity() {

    private lateinit var ui: SubAgentUi
    private lateinit var statusCard: LinearLayout
    private lateinit var autoSwitch: SwitchCompat
    private lateinit var htmlSwitch: SwitchCompat
    private lateinit var templateInput: EditText
    private lateinit var preview: WebView
    private lateinit var setupButton: android.widget.TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyTheme(this)
        super.onCreate(savedInstanceState)
        ui = SubAgentUi(this)
        setContentView(buildUi())
        refresh()
    }

    override fun onResume() {
        super.onResume()
        if (::statusCard.isInitialized) refresh()
    }

    override fun onDestroy() {
        if (::preview.isInitialized) {
            preview.destroy()
        }
        super.onDestroy()
    }

    private fun buildUi(): View {
        val root = FrameLayout(this)
        ThemeManager.applyDynamicBackground(root)

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(18), ui.dp(18), ui.dp(18), ui.dp(28))
        }
        column.addView(
            ui.header(
                titleText = "Email Agent",
                subtitleText = "Gmail auto reply with your own HTML design",
                onBack = { finish() }
            )
        )

        statusCard = ui.card().apply { setPadding(ui.dp(16), ui.dp(14), ui.dp(16), ui.dp(14)) }
        column.addView(statusCard, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = ui.dp(16) })

        setupButton = ui.pillButton("Gmail settings · 16-character App Password")
        setupButton.setOnClickListener { startActivity(Intent(this, GmailSettingsActivity::class.java)) }
        column.addView(setupButton, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = ui.dp(4); bottomMargin = ui.dp(16) })

        column.addView(switchCard(
            title = "Auto reply",
            subtitle = "Answers new unread Gmail for you",
            initial = GmailStore.autoReplyEnabled(this)
        ) { checked ->
            if (checked && !GmailStore.isConnected(this)) {
                Toast.makeText(this, "Connect Gmail first", Toast.LENGTH_SHORT).show()
                refresh()
                startActivity(Intent(this, GmailSettingsActivity::class.java))
                false
            } else {
                SubAgentStateResolver.setAutoRunning(
                    this,
                    SubAgentCatalog.byId(SubAgentCatalog.EMAIL_ID)!!,
                    checked
                )
                true
            }
        }.also { autoSwitch = it.second })

        column.addView(switchCard(
            title = "HTML reply design",
            subtitle = "Send the card below instead of plain text",
            initial = GmailStore.htmlReplyEnabled(this)
        ) { checked ->
            GmailStore.setHtmlReplyEnabled(this, checked)
            preview.visibility = if (checked) View.VISIBLE else View.GONE
            true
        }.also { htmlSwitch = it.second })

        column.addView(ui.title("Reply design", 15f).apply { setPadding(0, ui.dp(22), 0, ui.dp(6)) })
        column.addView(
            ui.hint(
                "Placeholders: {name} sender name · {email} sender address · {subject} subject · {reply} the " +
                    "AI or template reply text. Keep it mobile friendly; heavy tables and external images may be " +
                    "blocked by mail apps."
            )
        )

        templateInput = EditText(this).apply {
            setText(GmailStore.htmlTemplate(this))
            textSize = 11.5f
            typeface = Typeface.MONOSPACE
            setTextColor(ui.textPrimary)
            setHintTextColor(ui.textMuted)
            background = ui.rounded(ui.alpha(ui.cardBg, 0.9f), 14, ui.alpha(ui.cardBorder, 0.8f))
            setPadding(ui.dp(12), ui.dp(12), ui.dp(12), ui.dp(12))
            gravity = Gravity.TOP or Gravity.START
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 8
            setTextIsSelectable(true)
        }
        column.addView(templateInput, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = ui.dp(10) })

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, ui.dp(10), 0, 0)
        }
        val save = ui.pillButton("Save design")
        save.setOnClickListener { saveTemplate() }
        val starter = ui.pillButton("Starter", filled = false)
        starter.setOnClickListener {
            templateInput.setText(HtmlReplyTemplate.STARTER)
            renderPreview()
        }
        val previewButton = ui.pillButton("Preview", filled = false)
        previewButton.setOnClickListener { renderPreview() }
        actions.addView(save, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.2f).apply { marginEnd = ui.dp(8) })
        actions.addView(previewButton, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = ui.dp(8) })
        actions.addView(starter, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.9f))
        column.addView(actions)

        preview = WebView(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            settings.javaScriptEnabled = false
            settings.blockNetworkImage = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            visibility = if (GmailStore.htmlReplyEnabled(this@EmailSubAgentActivity)) View.VISIBLE else View.GONE
        }
        column.addView(preview, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            ui.dp(300)
        ).apply { topMargin = ui.dp(14) })

        column.addView(
            ui.card().apply {
                setPadding(ui.dp(14), ui.dp(12), ui.dp(14), ui.dp(12))
                addView(ui.title("How replies are sent", 13f))
                addView(
                    ui.hint(
                        "Jarvis checks unread mail about every 15 minutes (Android may delay it), skips newsletters, " +
                            "no-reply and automated mail, and stops at 10 replies per hour / 60 per day with 90 " +
                            "seconds between sends. Each reply goes as an HTML message with a plain-text fallback. " +
                            "Phone off or no internet means no replies."
                    ).apply { setPadding(0, ui.dp(6), 0, 0) }
                )
            }.apply { (layoutParams as LinearLayout.LayoutParams).topMargin = ui.dp(18) }
        )

        root.addView(ScrollView(this).apply {
            isFillViewport = true
            addView(column)
        })
        return root
    }

    private fun switchCard(
        title: String,
        subtitle: String,
        initial: Boolean,
        onChange: (Boolean) -> Boolean
    ): Pair<LinearLayout, SwitchCompat> {
        val card = ui.card().apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            addView(ui.title(title))
            addView(ui.hint(subtitle).apply { setPadding(0, ui.dp(3), 0, 0) })
        }
        val toggle = SwitchCompat(this).apply {
            isChecked = initial
            contentDescription = title
            setOnCheckedChangeListener { _, checked ->
                if (!onChange(checked)) {
                    setOnCheckedChangeListener(null)
                    isChecked = !checked
                    setOnCheckedChangeListener { _, c -> onChange(c) }
                } else {
                    refresh()
                }
            }
        }
        card.addView(column)
        card.addView(toggle)
        card.setOnClickListener { toggle.isChecked = !toggle.isChecked }
        return card to toggle
    }

    private fun refresh() {
        val connected = GmailStore.isConnected(this)
        val verified = GmailStore.connectionVerified(this)
        val enabled = GmailStore.autoReplyEnabled(this)

        statusCard.removeAllViews()
        val headline: String
        val tone: Int
        when {
            !connected -> {
                headline = "SETUP · Gmail not connected"
                tone = Color.parseColor("#D97706")
            }
            enabled -> {
                headline = "ACTIVE · auto replying"
                tone = Color.parseColor("#16A34A")
            }
            else -> {
                headline = "PAUSED · switch is off"
                tone = ui.textMuted
            }
        }
        statusCard.addView(ui.title(headline, 14f).apply { setTextColor(tone) })
        statusCard.addView(
            ui.hint(
                if (!connected)
                    "Save your Gmail address and Google's 16-character App Password. It stays encrypted on this phone."
                else (GmailStore.email(this).ifBlank { "Gmail" }) +
                    (if (verified) " · verified" else " · not verified yet") +
                    "\n" + GmailStore.scanStatus(this),
                11.5f
            ).apply { setPadding(0, ui.dp(6), 0, 0) }
        )

        autoSwitch.setOnCheckedChangeListener(null)
        autoSwitch.isChecked = enabled
        autoSwitch.isEnabled = connected
        autoSwitch.setOnCheckedChangeListener { _, checked ->
            if (checked && !connected) {
                startActivity(Intent(this, GmailSettingsActivity::class.java))
                refresh()
                return@setOnCheckedChangeListener
            }
            SubAgentStateResolver.setAutoRunning(this, SubAgentCatalog.byId(SubAgentCatalog.EMAIL_ID)!!, checked)
            refresh()
        }

        htmlSwitch.setOnCheckedChangeListener(null)
        htmlSwitch.isChecked = GmailStore.htmlReplyEnabled(this)
        htmlSwitch.setOnCheckedChangeListener { _, checked ->
            GmailStore.setHtmlReplyEnabled(this, checked)
            preview.visibility = if (checked) View.VISIBLE else View.GONE
            renderPreview()
        }

        if (templateInput.text.isNullOrBlank()) {
            templateInput.setText(GmailStore.htmlTemplate(this))
        }
        renderPreview()
    }

    private fun saveTemplate() {
        val template = templateInput.text?.toString().orEmpty().trim()
        if (template.isBlank()) {
            Toast.makeText(this, "Template is empty — restored the starter", Toast.LENGTH_SHORT).show()
            templateInput.setText(HtmlReplyTemplate.STARTER)
            GmailStore.setHtmlTemplate(this, HtmlReplyTemplate.STARTER)
        } else {
            GmailStore.setHtmlTemplate(this, template)
            Toast.makeText(this, "Reply design saved", Toast.LENGTH_SHORT).show()
        }
        renderPreview()
    }

    private fun renderPreview() {
        val template = templateInput.text?.toString().orEmpty().ifBlank { HtmlReplyTemplate.STARTER }
        val sampleReply = "Thank you for your email — I have received it and will get back to you with the " +
            "details shortly.\n\nBest regards,\nAyan"
        val html = HtmlReplyTemplate.render(template, "Ayan", "sender@example.com", "Your message", sampleReply)
        preview.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
    }
}