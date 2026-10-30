package com.jarvis.assistant.ui.settings

import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.jarvis.assistant.R
import com.jarvis.assistant.util.ThemeManager
import com.jarvis.assistant.voice.TtsVoiceCatalog
import com.jarvis.assistant.voice.TtsVoiceSelector
import com.jarvis.assistant.voice.VoiceOption
import com.jarvis.assistant.voice.VoicePreviewPlayer
import com.jarvis.assistant.voice.VoiceStyle
import com.jarvis.assistant.voice.VoiceStyles

/**
 * Voice Studio — lists the voices the device's TTS engine actually provides and plays a
 * real sample with the selected one. Nothing here is a placeholder recording.
 */
class VoiceStudioActivity : AppCompatActivity() {

    private lateinit var player: VoicePreviewPlayer
    private lateinit var listContainer: LinearLayout
    private lateinit var statusText: TextView
    private lateinit var engineText: TextView
    private lateinit var previewButton: Button
    private lateinit var stopButton: Button

    private lateinit var styleRow: LinearLayout
    private var allOptions: List<VoiceOption> = emptyList()
    private var selected: VoiceOption? = null
    private var selectedStyle: VoiceStyle = VoiceStyles.ALL.first()
    private var filterLanguage: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyTheme(this)
        super.onCreate(savedInstanceState)
        player = VoicePreviewPlayer(this)
        selectedStyle = VoiceStyles.current(this) ?: VoiceStyles.ALL.first()
        setContentView(buildRoot())
        bindPlayerEvents()
        player.withEngine { tts ->
            engineText.text = "Engine: " + TtsVoiceCatalog.engineLabel(tts)
            allOptions = TtsVoiceCatalog.options(tts)
            val saved = TtsVoiceSelector.savedKey(this)
            selected = allOptions.firstOrNull { it.key == saved }
            if (allOptions.isEmpty()) {
                statusText.text = "This device's TTS engine reports no installed voices."
            }
            renderList()
            updateButtons()
        }
    }

    private fun bindPlayerEvents() {
        player.onStarted = {
            statusText.text = "Playing preview…"
            stopButton.isEnabled = true
        }
        player.onFinished = {
            statusText.text = "Preview finished."
            stopButton.isEnabled = false
        }
        player.onError = { message ->
            statusText.text = message
            stopButton.isEnabled = false
        }
    }

    private fun buildRoot(): LinearLayout {
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
            setBackgroundColor(ContextCompat.getColor(this@VoiceStudioActivity, R.color.studio_surface))
        }

        column.addView(TextView(this).apply {
            text = "Voice Studio"
            textSize = 22f
        })
        column.addView(TextView(this).apply {
            text = "Pick a voice from the engine already configured on this device, then preview it."
            textSize = 13f
            setPadding(0, 8, 0, 12)
            setTextColor(ContextCompat.getColor(context, R.color.studio_text_secondary))
        })
        engineText = TextView(this).apply {
            text = "Engine: loading…"
            textSize = 12f
            setPadding(0, 0, 0, 8)
        }
        column.addView(engineText)

        column.addView(TextView(this).apply {
            text = "Voice style"
            textSize = 13f
            setPadding(0, 4, 0, 2)
        })
        column.addView(TextView(this).apply {
            text = "Prosody layered on any engine voice — same speaker, different character."
            textSize = 11f
            setTextColor(ContextCompat.getColor(context, R.color.studio_text_secondary))
        })
        styleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 6, 0, 6)
        }
        column.addView(HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(styleRow)
        })
        renderStyles()

        val filterLabel = TextView(this).apply { text = "Language filter"; textSize = 13f; setPadding(0, 8, 0, 4) }
        column.addView(filterLabel)
        val spinner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@VoiceStudioActivity,
                android.R.layout.simple_spinner_dropdown_item,
                listOf("All languages", "Bengali", "Hindi", "English")
            )
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                    filterLanguage = when (position) {
                        1 -> "bn"
                        2 -> "hi"
                        3 -> "en"
                        else -> ""
                    }
                    renderList()
                }

                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }
        }
        column.addView(spinner)

        listContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 12, 0, 12) }
        val scroll = ScrollView(this).apply {
            addView(listContainer)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        column.addView(scroll)

        statusText = TextView(this).apply {
            text = "Select a voice to preview."
            textSize = 13f
            setPadding(0, 8, 0, 8)
        }
        column.addView(statusText)

        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        previewButton = Button(this).apply {
            text = "Preview"
            setOnClickListener { previewSelected() }
        }
        stopButton = Button(this).apply {
            text = "Stop"
            isEnabled = false
            setOnClickListener {
                player.stop()
                statusText.text = "Stopped."
                stopButton.isEnabled = false
            }
        }
        val saveButton = Button(this).apply {
            text = "Save voice"
            setOnClickListener { saveSelected() }
        }
        actions.addView(previewButton, rowParams())
        actions.addView(stopButton, rowParams())
        actions.addView(saveButton, rowParams())
        column.addView(actions)

        column.addView(TextView(this).apply {
            text = "Voices listed here come from this Android TTS engine. Some network voices need " +
                "internet; offline voices work without it. A voice cannot be created by renaming another one."
            textSize = 11f
            setPadding(0, 12, 0, 0)
            setTextColor(ContextCompat.getColor(context, R.color.studio_text_secondary))
        })

        return column
    }

    private fun rowParams() = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)

    private fun renderStyles() {
        styleRow.removeAllViews()
        VoiceStyles.ALL.forEach { style ->
            val button = Button(this).apply {
                text = style.label
                textSize = 12f
                alpha = if (selectedStyle.key == style.key) 1f else 0.55f
                setOnClickListener {
                    selectedStyle = style
                    renderStyles()
                    statusText.text = style.label + " — " + style.description
                }
            }
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.setMargins(0, 0, 8, 0)
            styleRow.addView(button, params)
        }
    }

    private fun filteredOptions(): List<VoiceOption> =
        if (filterLanguage.isBlank()) allOptions else allOptions.filter { it.locale.language == filterLanguage }

    private fun renderList() {
        listContainer.removeAllViews()
        val options = filteredOptions()
        if (options.isEmpty()) {
            listContainer.addView(TextView(this).apply {
                text = if (allOptions.isEmpty()) "No voices available from the engine." else "No voices for this language on this device."
                textSize = 13f
                setPadding(0, 16, 0, 16)
            })
            return
        }
        options.forEach { option ->
            listContainer.addView(buildRow(option))
        }
    }

    private fun buildRow(option: VoiceOption): LinearLayout {
        val isSelected = selected?.key == option.key
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 14, 20, 14)
            background = ContextCompat.getDrawable(this@VoiceStudioActivity, R.drawable.bg_studio_card)
            setOnClickListener {
                selected = option
                renderList()
                updateButtons()
                statusText.text = "${option.name} selected — tap Preview to hear it."
            }
        }
        row.addView(TextView(this).apply {
            text = (if (isSelected) "● " else "○ ") + option.name
            textSize = 15f
        })
        row.addView(TextView(this).apply {
            text = "${option.languageLabel} · ${option.accent} · ${option.provider}"
            textSize = 12f
            setTextColor(ContextCompat.getColor(context, R.color.studio_text_secondary))
        })
        row.addView(TextView(this).apply {
            text = option.availabilityLabel
            textSize = 11f
            setTextColor(ContextCompat.getColor(context, R.color.studio_text_secondary))
        })
        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        params.setMargins(0, 0, 0, 10)
        row.layoutParams = params
        return row
    }

    private fun updateButtons() {
        previewButton.isEnabled = selected != null
        stopButton.isEnabled = false
    }

    private fun previewSelected() {
        val option = selected ?: run {
            statusText.text = "Select a voice first."
            return
        }
        statusText.text = "Preparing preview…"
        val started = player.preview(option, TtsVoiceCatalog.previewText(option.locale), selectedStyle)
        if (!started) {
            if (statusText.text == "Preparing preview…") statusText.text = "This voice could not be synthesised."
        }
    }

    private fun saveSelected() {
        val option = selected ?: run {
            statusText.text = "Select a voice first."
            return
        }
        TtsVoiceSelector.save(this, option)
        VoiceStyles.save(this, selectedStyle)
        Toast.makeText(this, "Voice saved: ${option.name} · ${selectedStyle.label}", Toast.LENGTH_SHORT).show()
        statusText.text = "Saved. Jarvis will speak with this voice and style."
    }

    override fun onStop() {
        super.onStop()
        player.stop()
    }

    override fun onDestroy() {
        player.release()
        super.onDestroy()
    }
}