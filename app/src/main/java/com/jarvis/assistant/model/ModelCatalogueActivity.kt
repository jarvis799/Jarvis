package com.jarvis.assistant.model

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.jarvis.assistant.util.ThemeManager
import kotlinx.coroutines.launch
import java.io.File

/**
 * DEV STYLE: model catalogue + downloader UI.
 * Search Hugging Face, see a compatibility verdict, download with pause/resume, and pick the active model.
 */
class ModelCatalogueActivity : AppCompatActivity() {

    private lateinit var store: ModelStore
    private lateinit var searchField: EditText
    private lateinit var statusView: TextView
    private lateinit var resultsContainer: LinearLayout
    private lateinit var installedContainer: LinearLayout
    private val downloader by lazy { ModelDownloader(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyTheme(this)
        super.onCreate(savedInstanceState)
        store = ModelStore(this)
        buildUi()
        refreshInstalled()
    }

    override fun onDestroy() {
        downloader.pause()
        super.onDestroy()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }
        root.addView(TextView(this).apply { text = "Local AI Models"; textSize = 22f })
        root.addView(TextView(this).apply {
            text = LocalModelRuntime.statusMessage
            textSize = 11f
            setPadding(0, 6, 0, 12)
        })

        root.addView(TextView(this).apply { text = "Installed"; textSize = 15f })
        installedContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(installedContainer)

        root.addView(TextView(this).apply {
            text = "Download a model (GGUF)"
            textSize = 15f
            setPadding(0, 18, 0, 6)
        })
        val searchRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        searchField = EditText(this).apply { hint = "e.g. tinyllama, qwen, phi"; textSize = 13f }
        searchRow.addView(searchField, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        searchRow.addView(Button(this).apply {
            text = "Search"
            setOnClickListener { search() }
        })
        root.addView(searchRow)

        statusView = TextView(this).apply {
            text = "Tap Search to load models."
            textSize = 11f
            setPadding(0, 6, 0, 6)
        }
        root.addView(statusView)

        resultsContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            addView(resultsContainer)
        })
        setContentView(root)
    }

    private fun search() {
        val query = searchField.text.toString().trim()
        statusView.text = "Searching Hugging Face…"
        resultsContainer.removeAllViews()
        lifecycleScope.launch {
            val result = runCatching { ModelCatalogue.search(query) }
            if (isFinishing || isDestroyed) return@launch
            result.onSuccess { models ->
                statusView.text = if (models.isEmpty()) "No GGUF models found." else "${models.size} models"
                models.forEach { resultsContainer.addView(resultRow(it)) }
            }.onFailure {
                statusView.text = "Search failed: ${it.message}"
            }
        }
    }

    private fun resultRow(model: RemoteModel): LinearLayout {
        val compat = ModelCompatibility.check(this, model.sizeBytes)
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 14, 0, 14)
        }
        row.addView(TextView(this).apply { text = model.name; textSize = 14f })
        row.addView(TextView(this).apply {
            text = "${sizeMb(model.sizeBytes)} MB • ${model.quant.ifBlank { "GGUF" }} • ${model.downloads} downloads"
            textSize = 11f
        })
        val note = TextView(this).apply {
            text = compat.reason
            textSize = 11f
            setPadding(0, 4, 0, 4)
        }
        row.addView(note)
        val button = Button(this).apply { text = if (compat.supported) "Download" else "Download anyway" }
        button.setOnClickListener { startDownload(model, button, note) }
        row.addView(button)
        return row
    }

    private fun startDownload(model: RemoteModel, button: Button, note: TextView) {
        if (downloader.isRunning()) {
            Toast.makeText(this, "Pause the running download first.", Toast.LENGTH_SHORT).show()
            return
        }
        if (store.models().any { it.id == model.id }) {
            Toast.makeText(this, "This model is already installed.", Toast.LENGTH_SHORT).show()
            return
        }
        button.isEnabled = false
        button.text = "Downloading…"
        note.text = "Starting…"
        downloader.start(
            model = model,
            onProgress = { done, total ->
                runOnUiThread {
                    note.text = if (total > 0) {
                        "${done * 100 / total}% of ${sizeMb(total)} MB — Pause keeps the partial file."
                    } else {
                        "${sizeMb(done)} MB downloaded — Pause keeps the partial file."
                    }
                }
            },
            onDone = { file ->
                runOnUiThread {
                    button.isEnabled = true
                    if (file != null && file.exists()) {
                        store.add(
                            LocalModel(
                                id = model.id,
                                name = model.name,
                                filePath = file.absolutePath,
                                sizeBytes = file.length(),
                                downloadedAt = System.currentTimeMillis(),
                                quant = model.quant
                            )
                        )
                        button.text = "Installed"
                        note.text = "Saved. Pick it under Installed."
                        refreshInstalled()
                    } else {
                        button.text = "Download"
                        note.text = "Download failed."
                    }
                }
            },
            onError = { message ->
                runOnUiThread {
                    button.isEnabled = true
                    button.text = "Resume"
                    note.text = message
                    button.setOnClickListener { startDownload(model, button, note) }
                }
            }
        )
        button.setOnClickListener { pause(model, button, note) }
    }

    private fun pause(model: RemoteModel, button: Button, note: TextView) {
        downloader.pause()
        button.isEnabled = true
        button.text = "Resume"
        note.text = "Paused. The partial file is kept on this phone."
        button.setOnClickListener { startDownload(model, button, note) }
    }

    private fun refreshInstalled() {
        installedContainer.removeAllViews()
        val models = store.models()
        if (models.isEmpty()) {
            installedContainer.addView(TextView(this).apply {
                text = "No models installed yet."
                textSize = 12f
                setPadding(0, 6, 0, 6)
            })
            return
        }
        val activeId = store.activeModelId()
        models.forEach { model ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 10, 0, 10)
            }
            row.addView(TextView(this).apply {
                text = model.name + if (model.id == activeId) "   •  ACTIVE" else ""
                textSize = 14f
            })
            row.addView(TextView(this).apply {
                text = "${sizeMb(model.sizeBytes)} MB • ${model.filePath.substringAfterLast('/')}"
                textSize = 11f
            })
            val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            buttons.addView(Button(this).apply {
                text = "Set active"
                setOnClickListener {
                    store.setActive(model.id)
                    refreshInstalled()
                    Toast.makeText(
                        this@ModelCatalogueActivity,
                        "Active model: ${model.name}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            })
            buttons.addView(Button(this).apply {
                text = "Delete"
                setOnClickListener {
                    runCatching { File(model.filePath).delete() }
                    store.remove(model.id)
                    refreshInstalled()
                }
            })
            row.addView(buttons)
            installedContainer.addView(row)
        }
    }

    private fun sizeMb(bytes: Long): String = (bytes / (1024 * 1024)).toString()
}