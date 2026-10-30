package com.jarvis.assistant.subagent

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.SwitchCompat
import com.jarvis.assistant.R
import com.jarvis.assistant.ui.permission.PermissionActivity
import com.jarvis.assistant.util.ThemeManager

/**
 * DEV STYLE — Sub Agent Showcase.
 * A real hub, not a mock: each card either toggles a working background agent
 * (WhatsApp / Email auto reply) or opens the installed Jarvis tool that powers it.
 */
class SubAgentActivity : AppCompatActivity() {

    private lateinit var ui: SubAgentUi
    private var category = SubAgentCategory.PROFESSIONAL
    private lateinit var listContainer: LinearLayout
    private lateinit var professionalTab: LinearLayout
    private lateinit var premiumTab: LinearLayout
    private lateinit var professionalLabel: android.widget.TextView
    private lateinit var premiumLabel: android.widget.TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyTheme(this)
        super.onCreate(savedInstanceState)
        ui = SubAgentUi(this)
        setContentView(buildUi())
        render()
    }

    override fun onResume() {
        super.onResume()
        if (::listContainer.isInitialized) render()
    }

    private fun buildUi(): View {
        val root = FrameLayout(this)
        ThemeManager.applyDynamicBackground(root)

        val main = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(18), ui.dp(18), ui.dp(18), 0)
        }
        main.addView(
            ui.header(
                titleText = "Sub Agent Showcase",
                subtitleText = "Specialized AI agents for different tasks",
                onBack = { finish() },
                action = overflowButton()
            )
        )

        val tabRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = ui.rounded(ui.alpha(ui.cardBg, 0.7f), 20, ui.alpha(ui.cardBorder, 0.6f))
            setPadding(ui.dp(4), ui.dp(4), ui.dp(4), ui.dp(4))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = ui.dp(16) }
        }
        val professional = tab("Professional") { switchCategory(SubAgentCategory.PROFESSIONAL) }
        val premium = tab("Premium") { switchCategory(SubAgentCategory.PREMIUM) }
        professionalTab = professional.first
        professionalLabel = professional.second
        premiumTab = premium.first
        premiumLabel = premium.second
        tabRow.addView(professionalTab, LinearLayout.LayoutParams(0, ui.dp(40), 1f))
        tabRow.addView(premiumTab, LinearLayout.LayoutParams(0, ui.dp(40), 1f))
        main.addView(tabRow)

        listContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, ui.dp(16), 0, ui.dp(24))
        }
        main.addView(
            ScrollView(this).apply {
                isFillViewport = true
                addView(listContainer)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    0,
                    1f
                )
            }
        )
        root.addView(main)
        return root
    }

    private fun tab(label: String, onClick: () -> Unit): Pair<LinearLayout, android.widget.TextView> {
        val text = android.widget.TextView(this).apply {
            this.text = label
            textSize = 13.5f
            gravity = Gravity.CENTER
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD)
            setTextColor(ui.textSecondary)
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            addView(text)
            setOnClickListener { onClick() }
        }
        return container to text
    }

    private fun switchCategory(next: SubAgentCategory) {
        if (category == next) return
        category = next
        render()
    }

    private fun render() {
        renderTabs()
        listContainer.removeAllViews()

        SubAgentCatalog.inCategory(category).forEach { agent ->
            listContainer.addView(agentCard(agent))
        }

        listContainer.addView(
            ui.card().apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(ui.iconBubble(ui.accent, R.drawable.ic_sparkle))
                val column = LinearLayout(this@SubAgentActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(ui.dp(12), 0, 0, 0)
                    addView(ui.title(if (category == SubAgentCategory.PROFESSIONAL) "Professional Agents" else "Premium Agents", 14f))
                    addView(
                        ui.hint(
                            if (category == SubAgentCategory.PROFESSIONAL)
                                "Every card opens a real Jarvis tool already installed on this phone"
                            else
                                "Heavier tools. They run only when you open them",
                            11.5f
                        ).apply { setPadding(0, ui.dp(3), 0, 0) }
                    )
                }
                addView(column)
            }
        )
    }

    private fun renderTabs() {
        val selectedBg = ui.rounded(ui.accent, 16)
        val unselectedBg = ui.rounded(Color.TRANSPARENT, 16)
        val professionalSelected = category == SubAgentCategory.PROFESSIONAL
        professionalTab.background = if (professionalSelected) selectedBg else unselectedBg
        premiumTab.background = if (professionalSelected) unselectedBg else selectedBg
        professionalLabel.setTextColor(if (professionalSelected) Color.parseColor("#0B1F10") else ui.textSecondary)
        premiumLabel.setTextColor(if (professionalSelected) Color.parseColor("#0B1F10") else ui.textSecondary)
    }

    private fun agentCard(agent: SubAgent): LinearLayout {
        val status = SubAgentStateResolver.status(this, agent)
        val card = ui.card()
        card.isClickable = true
        card.isFocusable = true
        card.contentDescription = agent.name + ". " + agent.description

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(ui.iconBubble(toneFor(status.state), iconFor(agent.icon)))

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                .apply { marginStart = ui.dp(12); marginEnd = ui.dp(8) }
        }
        column.addView(ui.title(agent.name))
        column.addView(ui.body(agent.description).apply { setPadding(0, ui.dp(3), 0, 0) })

        val badgeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, ui.dp(8), 0, 0)
        }
        badgeRow.addView(ui.badge(status.label, toneFor(status.state)))
        badgeRow.addView(
            ui.hint(status.detail, 11f).apply { setPadding(ui.dp(8), 0, 0, 0) }
        )
        column.addView(badgeRow)
        row.addView(column)

        if (agent.autoRuns && status.state != SubAgentState.SETUP) {
            row.addView(
                SwitchCompat(this).apply {
                    isChecked = status.state == SubAgentState.RUNNING
                    contentDescription = "Turn " + agent.name + " on or off"
                    setOnCheckedChangeListener { _, checked ->
                        if (!SubAgentStateResolver.setAutoRunning(this@SubAgentActivity, agent, checked)) {
                            openTarget(agent)
                        }
                        render()
                    }
                }
            )
            card.setOnClickListener {
                val next = status.state != SubAgentState.RUNNING
                SubAgentStateResolver.setAutoRunning(this, agent, next)
                Toast.makeText(
                    this,
                    agent.name + (if (next) " is on" else " is paused"),
                    Toast.LENGTH_SHORT
                ).show()
                render()
            }
        } else {
            row.addView(
                android.widget.TextView(this).apply {
                    text = "›"
                    textSize = 20f
                    setTextColor(ui.textMuted)
                }
            )
            card.setOnClickListener { openTarget(agent) }
        }

        card.addView(row)
        return card
    }

    private fun openTarget(agent: SubAgent) {
        val destination: Class<*> = when (agent.target) {
            SubAgentTarget.WHATSAPP_AUTO_REPLY -> WhatsAppSubAgentActivity::class.java
            SubAgentTarget.EMAIL_AUTO_REPLY -> EmailSubAgentActivity::class.java
            SubAgentTarget.BROWSER_AGENT -> com.jarvis.assistant.browser.BrowserAgentActivity::class.java
            SubAgentTarget.TERMINAL -> com.jarvis.assistant.terminal.TerminalActivity::class.java
            SubAgentTarget.FILE_ORGANIZER -> com.jarvis.assistant.ui.settings.FileOrganizerActivity::class.java
            SubAgentTarget.SCREEN_CONTEXT -> com.jarvis.assistant.screencontext.ScreenContextActivity::class.java
            SubAgentTarget.DEV_STUDIO -> com.jarvis.assistant.ui.devstudio.DevStudioActivity::class.java
            SubAgentTarget.APP_CONTROL -> com.jarvis.assistant.ui.settings.HandsFreeActivity::class.java
            SubAgentTarget.RESEARCH_LAB -> com.jarvis.assistant.developer.GitHubResearchActivity::class.java
        }
        startActivity(Intent(this, destination))
    }

    private fun overflowButton(): View {
        val button = LinearLayout(this).apply {
            background = ui.rounded(ui.alpha(ui.cardBg, 0.8f), 14, ui.alpha(ui.cardBorder, 0.5f))
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            contentDescription = "More options"
            layoutParams = LinearLayout.LayoutParams(ui.dp(40), ui.dp(40))
            addView(
                android.widget.ImageView(this@SubAgentActivity).apply {
                    setImageResource(R.drawable.ic_menu)
                    imageTintList = android.content.res.ColorStateList.valueOf(ui.textPrimary)
                    layoutParams = LinearLayout.LayoutParams(ui.dp(18), ui.dp(18))
                    contentDescription = null
                }
            )
        }
        button.setOnClickListener { anchor ->
            PopupMenu(this, anchor).apply {
                menu.add("Agent permissions")
                menu.add("Refresh status")
                menu.add("How sub agents work")
                setOnMenuItemClickListener { item ->
                    when (item.title.toString()) {
                        "Agent permissions" -> startActivity(Intent(this@SubAgentActivity, PermissionActivity::class.java))
                        "Refresh status" -> render()
                        else -> showHowItWorks()
                    }
                    true
                }
                show()
            }
        }
        return button
    }

    private fun showHowItWorks() {
        AlertDialog.Builder(this)
            .setTitle("How sub agents work")
            .setMessage(
                "A sub agent is a small specialist that Jarvis hands a task to.\n\n" +
                    "WhatsApp Agent reads incoming WhatsApp notifications and replies through WhatsApp's own " +
                    "reply action — no WhatsApp opening, no password stored.\n\n" +
                    "Email Agent checks your Gmail with the 16-character App Password you saved and sends your " +
                    "HTML designed reply automatically. Google limits it to 10 replies per hour.\n\n" +
                    "Every other card opens the real Jarvis tool: nothing is faked, and nothing runs without " +
                    "you switching it on."
            )
            .setPositiveButton("Got it", null)
            .show()
    }

    private fun toneFor(state: SubAgentState): Int = when (state) {
        SubAgentState.RUNNING -> Color.parseColor("#16A34A")
        SubAgentState.READY -> ui.accent
        SubAgentState.SETUP -> Color.parseColor("#D97706")
        SubAgentState.PAUSED -> ui.textMuted
    }

    private fun iconFor(icon: SubAgentIcon): Int = when (icon) {
        SubAgentIcon.WEB -> R.drawable.ic_settings_language
        SubAgentIcon.CODE -> R.drawable.ic_code_chip
        SubAgentIcon.CART -> R.drawable.ic_qr_box
        SubAgentIcon.PHONE -> R.drawable.ic_settings_apps
        SubAgentIcon.FOLDER -> R.drawable.ic_settings_db
        SubAgentIcon.SEARCH -> R.drawable.ic_scanner_radar
        SubAgentIcon.BUILD -> R.drawable.ic_settings_orb
        SubAgentIcon.PYTHON -> R.drawable.ic_settings_plug
        SubAgentIcon.CHAT -> R.drawable.ic_chat_bubble
        SubAgentIcon.MAIL -> R.drawable.ic_send_up
        SubAgentIcon.SCREEN -> R.drawable.ic_nav_scan2
    }
}