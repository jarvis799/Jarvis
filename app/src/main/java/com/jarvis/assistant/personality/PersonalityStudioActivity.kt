package com.jarvis.assistant.personality

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.jarvis.assistant.R
import com.jarvis.assistant.ui.settings.DeveloperProfileActivity
import com.jarvis.assistant.ui.settings.VoiceStudioActivity
import com.jarvis.assistant.util.ThemeManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Personality Studio — every control writes to [PersonalityStore], which the existing
 * LLM pipeline reads through PromptGenerator. No decorative switches.
 */
class PersonalityStudioActivity : AppCompatActivity() {

    private lateinit var store: PersonalityStore
    private lateinit var profile: PersonalityProfile

    private lateinit var humorButton: Button
    private lateinit var sarcasmButton: Button
    private lateinit var styleButton: Button
    private lateinit var roastingSwitch: Switch
    private lateinit var emojiSwitch: Switch
    private lateinit var enableCustomSwitch: Switch
    private lateinit var promptEditor: EditText
    private lateinit var savedStateText: TextView
    private lateinit var previewText: TextView
    private lateinit var addressEditor: EditText
    private lateinit var modeLabel: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyTheme(this)
        super.onCreate(savedInstanceState)
        store = PersonalityStore(this)
        profile = store.load()
        setContentView(buildRoot())
        renderState()
    }

    private fun buildRoot(): LinearLayout {
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 28, 28, 28)
            setBackgroundColor(ContextCompat.getColor(this@PersonalityStudioActivity, R.color.studio_surface))
        }

        column.addView(title("Personality Studio"))
        column.addView(hint("Shape how Jarvis speaks. These settings apply to the live voice conversation."))

        // ---------- Humor & roasting ----------
        column.addView(sectionHeading("Humor & Roasting"))
        humorButton = actionRow(column) { showChoices("Humor", HumorLevel.entries.map { it.name }) { index ->
            profile = profile.copy(humor = HumorLevel.entries[index])
            persist()
        } }
        sarcasmButton = actionRow(column) { showChoices("Sarcasm", SarcasmLevel.entries.map { it.name }) { index ->
            profile = profile.copy(sarcasm = SarcasmLevel.entries[index])
            persist()
        } }
        styleButton = actionRow(column) { showChoices("Response style", ResponseStyle.entries.map { it.name }) { index ->
            profile = profile.copy(style = ResponseStyle.entries[index])
            persist()
        } }

        roastingSwitch = Switch(this).apply {
            text = "Playful roasting"
            setOnCheckedChangeListener { _, checked ->
                profile = profile.copy(roasting = checked)
                persist()
            }
        }
        column.addView(roastingSwitch)

        emojiSwitch = Switch(this).apply {
            text = "Emoji in on-screen text"
            setOnCheckedChangeListener { _, checked ->
                profile = profile.copy(emoji = checked)
                persist()
            }
        }
        column.addView(emojiSwitch)

        // ---------- Custom personality ----------
        column.addView(sectionHeading("Custom Personality"))
        column.addView(hint("Describe how Jarvis should talk. It changes tone only — safety and honesty rules always win."))

        enableCustomSwitch = Switch(this).apply {
            text = "Use custom personality"
            setOnCheckedChangeListener { _, checked ->
                profile = profile.copy(customPromptEnabled = checked)
                persist()
            }
        }
        column.addView(enableCustomSwitch)

        promptEditor = EditText(this).apply {
            hint = "Write how Jarvis should speak…"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 6
            gravity = android.view.Gravity.TOP or android.view.Gravity.START
            setText(profile.customPrompt.ifBlank { PersonalityDefaults.CUSTOM_PROMPT_EXAMPLE })
            setPadding(24, 20, 24, 20)
        }
        column.addView(promptEditor)

        val editorRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        editorRow.addView(Button(this).apply {
            text = "Save"
            setOnClickListener { savePrompt(enabled = enableCustomSwitch.isChecked) }
        })
        editorRow.addView(Button(this).apply {
            text = "Cancel"
            setOnClickListener {
                promptEditor.setText(profile.customPrompt.ifBlank { PersonalityDefaults.CUSTOM_PROMPT_EXAMPLE })
                enableCustomSwitch.isChecked = profile.customPromptEnabled
                savedStateText.text = "Reverted to the last saved prompt."
            }
        })
        editorRow.addView(Button(this).apply {
            text = "Reset to default"
            setOnClickListener { promptEditor.setText(PersonalityDefaults.CUSTOM_PROMPT_EXAMPLE) }
        })
        editorRow.addView(Button(this).apply {
            text = "Preview"
            setOnClickListener {
                previewText.text = styleSample(previewProfile())
                previewText.visibility = TextView.VISIBLE
            }
        })
        column.addView(editorRow)

        savedStateText = TextView(this).apply {
            textSize = 12f
            setPadding(0, 6, 0, 10)
            setTextColor(ContextCompat.getColor(context, R.color.studio_text_secondary))
        }
        column.addView(savedStateText)

        previewText = TextView(this).apply {
            textSize = 14f
            visibility = TextView.GONE
            setPadding(0, 4, 0, 12)
        }
        column.addView(previewText)
        column.addView(hint("The preview is a local style sample built from your settings, not a live AI reply."))

        // ---------- Voice mode ----------
        column.addView(sectionHeading("Voice Mode"))
        modeLabel = TextView(this).apply { textSize = 13f; setPadding(0, 4, 0, 8) }
        column.addView(modeLabel)
        val modeRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        VoiceMode.entries.forEach { mode ->
            modeRow.addView(Button(this).apply {
                text = mode.displayName
                setOnClickListener {
                    profile = profile.copy(voiceMode = mode)
                    persist()
                    Toast.makeText(this@PersonalityStudioActivity, mode.displayName + " selected", Toast.LENGTH_SHORT).show()
                }
            })
        }
        column.addView(modeRow)
        column.addView(hint("These are optional speaking styles, not separate AI models. Dependency, jealousy and pressure to keep using the app are never simulated."))

        // ---------- Address ----------
        column.addView(sectionHeading("How Jarvis addresses you"))
        addressEditor = EditText(this).apply {
            hint = "Boss"
            setText(profile.addressName)
            inputType = InputType.TYPE_CLASS_TEXT
        }
        column.addView(addressEditor)
        column.addView(Button(this).apply {
            text = "Save address"
            setOnClickListener {
                val name = addressEditor.text.toString().trim()
                profile = profile.copy(addressName = name.ifBlank { "Boss" })
                persist()
                Toast.makeText(this@PersonalityStudioActivity, "Saved.", Toast.LENGTH_SHORT).show()
            }
        })

        // ---------- Links ----------
        column.addView(sectionHeading("Voice & Developer"))
        column.addView(Button(this).apply {
            text = "Voice Studio (select & preview voice)"
            setOnClickListener { startActivity(Intent(this@PersonalityStudioActivity, VoiceStudioActivity::class.java)) }
        })
        column.addView(Button(this).apply {
            text = "Developer Information"
            setOnClickListener { startActivity(Intent(this@PersonalityStudioActivity, DeveloperProfileActivity::class.java)) }
        })
        column.addView(Button(this).apply {
            text = "About the TTS voice engine"
            setOnClickListener { showEngineInfo() }
        })

        val scroll = ScrollView(this).apply {
            addView(column)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(scroll)
            addView(Button(this@PersonalityStudioActivity).apply {
                text = "Close"
                setOnClickListener { finish() }
            })
        }
    }

    private fun title(text: String) = TextView(this).apply { this.text = text; textSize = 22f }

    private fun hint(text: String) = TextView(this).apply {
        this.text = text
        textSize = 12f
        setPadding(0, 4, 0, 10)
        setTextColor(ContextCompat.getColor(context, R.color.studio_text_secondary))
    }

    private fun sectionHeading(text: String) = TextView(this).apply {
        this.text = text
        textSize = 16f
        setPadding(0, 20, 0, 6)
    }

    private fun actionRow(parent: LinearLayout, onClick: () -> Unit): Button {
        val button = Button(this)
        button.setOnClickListener { onClick() }
        parent.addView(button)
        return button
    }

    private fun showChoices(title: String, choices: List<String>, onPicked: (Int) -> Unit) {
        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setItems(choices.toTypedArray()) { _, which -> onPicked(which) }
            .show()
    }

    private fun persist() {
        store.save(profile)
        renderState()
    }

    private fun savePrompt(enabled: Boolean) {
        val typed = promptEditor.text.toString()
        val clean = PersonalityComposer.sanitizeCustomPrompt(typed)
        if (clean.isBlank()) {
            profile = profile.copy(customPrompt = "", customPromptEnabled = false)
            store.save(profile)
            enableCustomSwitch.isChecked = false
            renderState()
            Toast.makeText(this, "Prompt is empty, so custom personality stays off.", Toast.LENGTH_LONG).show()
            return
        }
        if (clean.length < typed.trim().length) {
            Toast.makeText(this, "Lines that tried to override app rules were removed.", Toast.LENGTH_LONG).show()
        }
        profile = profile.copy(customPrompt = clean, customPromptEnabled = enabled)
        store.save(profile)
        promptEditor.setText(clean)
        renderState()
        Toast.makeText(this, "Custom personality saved.", Toast.LENGTH_SHORT).show()
    }

    private fun renderState() {
        humorButton.text = "Humor: " + profile.humor.name
        sarcasmButton.text = "Sarcasm: " + profile.sarcasm.name
        styleButton.text = "Response style: " + profile.style.name
        roastingSwitch.isChecked = profile.roasting
        emojiSwitch.isChecked = profile.emoji
        enableCustomSwitch.isChecked = profile.customPromptEnabled
        modeLabel.text = "Current mode: " + profile.voiceMode.displayName
        val savedAt = store.savedAt()
        savedStateText.text = if (savedAt <= 0L) {
            "Not saved yet."
        } else {
            val stamp = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(savedAt))
            val promptState = if (profile.customPromptEnabled) "enabled" else "disabled"
            "Saved $stamp · custom prompt $promptState (" + profile.customPrompt.length + "/" +
                PersonalityDefaults.MAX_PROMPT_CHARS + " characters)"
        }
    }

    private fun previewProfile(): PersonalityProfile {
        val clean = PersonalityComposer.sanitizeCustomPrompt(promptEditor.text.toString())
        return profile.copy(customPrompt = clean, customPromptEnabled = enableCustomSwitch.isChecked)
    }

    /** Deterministic local sample so the boss can hear/see the tone before a real reply. */
    private fun styleSample(sample: PersonalityProfile): String {
        val address = sample.addressName.ifBlank { "Boss" }
        val humorLine = when (sample.humor) {
            HumorLevel.OFF -> "Sure, $address — I'll keep it straight."
            HumorLevel.LOW -> "On it, $address. Small joke later, maybe."
            HumorLevel.MEDIUM -> "On it, $address — and no, I won't pretend this was my idea."
            HumorLevel.HIGH -> "Boss, that plan has confidence I can only admire from a safe distance."
        }
        val roastLine = if (sample.roasting) " Want to run a whole data centre off that idea too?" else ""
        val modeLine = when (sample.voiceMode) {
            VoiceMode.OFF -> ""
            VoiceMode.GIRLFRIEND -> " I'm right here with you while it runs."
            VoiceMode.BOYFRIEND -> " I've got your back on this one."
        }
        val customLine = if (sample.customPromptEnabled && sample.customPrompt.isNotBlank()) {
            "\n\nCustom instructions in use: " + sample.customPrompt.lineSequence().firstOrNull().orEmpty()
        } else {
            ""
        }
        return humorLine + roastLine + modeLine + customLine
    }

    private fun showEngineInfo() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Jarvis Assistant Voice Model")
            .setMessage(
                "Jarvis speaks through the Text-to-Speech engine installed on this device, so the " +
                    "available voices, languages and offline support are exactly what that engine reports " +
                    "in Voice Studio.\n\n" +
                    "No separate custom Jarvis voice model is bundled with the app: a voice cannot be created " +
                    "by renaming an existing one. If you install a compatible engine or voice data, it appears " +
                    "in the list and can be previewed and saved."
            )
            .setPositiveButton("Open Voice Studio") { _, _ ->
                startActivity(Intent(this, VoiceStudioActivity::class.java))
            }
            .setNegativeButton("Close", null)
            .show()
    }
}