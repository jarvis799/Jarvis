package com.jarvis.assistant.bgremove

import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.core.view.WindowInsetsControllerCompat
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.jarvis.assistant.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * AI Background Remover screen. White surface with lime accents, before/after preview,
 * and a plain statement of where the work happens. Nothing is claimed until it really happened.
 */
class BackgroundRemovalActivity : AppCompatActivity() {

    private lateinit var config: BgRemovalConfig
    private lateinit var engine: BackgroundRemovalEngine

    private lateinit var statusText: TextView
    private lateinit var planText: TextView
    private lateinit var resultText: TextView
    private lateinit var capabilityText: TextView
    private lateinit var selectionText: TextView
    private lateinit var hostField: EditText
    private lateinit var progress: ProgressBar
    private lateinit var beforeView: ImageView
    private lateinit var afterView: ImageView
    private lateinit var previewButton: Button
    private lateinit var saveButton: Button
    private lateinit var retryButton: Button
    private lateinit var cancelButton: Button
    private lateinit var removeButton: Button

    private var selected: ImageInput? = null
    private var resultFile: File? = null
    private var job: Job? = null
    private var viewMode = MODE_BOTH

    private val pickImage = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri == null) {
            statusText.text = "No image selected. The original file is never changed."
            return@registerForActivityResult
        }
        when (val check = ImageInputValidator.check(this, uri)) {
            is InputCheck.Ok -> {
                selected = check.input
                resultFile = null
                afterView.setImageDrawable(null)
                viewMode = MODE_BEFORE
                showBefore()
                selectionText.text = check.input.displayName + "  ·  " + readableSize(check.input.sizeBytes) + "  ·  " + check.input.mimeType
                statusText.text = "Image ready. Tap Remove background."
                resultText.text = ""
                removeButton.isEnabled = true
                saveButton.isEnabled = false
                retryButton.isEnabled = false
                cancelButton.isEnabled = false
            }
            is InputCheck.Rejected -> {
                selected = null
                selectionText.text = "No valid image selected."
                statusText.text = check.message
                removeButton.isEnabled = false
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_background_removal)
        window.statusBarColor = Color.WHITE
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true

        config = BgRemovalConfig(this)
        engine = BackgroundRemovalEngine(this)

        statusText = findViewById(R.id.bgStatus)
        planText = findViewById(R.id.bgPlan)
        resultText = findViewById(R.id.bgResult)
        capabilityText = findViewById(R.id.bgCapability)
        selectionText = findViewById(R.id.bgSelection)
        hostField = findViewById(R.id.bgHostUrl)
        progress = findViewById(R.id.bgProgress)
        beforeView = findViewById(R.id.bgImageBefore)
        afterView = findViewById(R.id.bgImageAfter)
        previewButton = findViewById(R.id.btnBgPreview)
        saveButton = findViewById(R.id.btnBgSave)
        retryButton = findViewById(R.id.btnBgRetry)
        cancelButton = findViewById(R.id.btnBgCancel)
        removeButton = findViewById(R.id.btnBgRemove)

        findViewById<Button>(R.id.btnBgBack).setOnClickListener { finish() }
        findViewById<Button>(R.id.btnBgPick).setOnClickListener {
            pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        removeButton.setOnClickListener { startRemoval() }
        retryButton.setOnClickListener { startRemoval() }
        previewButton.setOnClickListener { cyclePreview() }
        saveButton.setOnClickListener { saveResult() }
        cancelButton.setOnClickListener {
            job?.cancel()
            job = null
            setBusy(false)
            statusText.text = BgRemovalErrors.CANCELLED + " The host may still finish on its own."
        }
        findViewById<Button>(R.id.btnBgTestHost).setOnClickListener { testHost() }

        hostField.setText(config.hostUrl)
        planText.text = engine.plan().note
        removeButton.isEnabled = false
        saveButton.isEnabled = false
        retryButton.isEnabled = false
        cancelButton.isEnabled = false
        probeTerminal()
    }

    /** Real check of the in-app terminal, so the user learns the truth instead of a promise. */
    private fun probeTerminal() {
        capabilityText.text = "Checking the terminal Python runtime..."
        lifecycleScope.launch {
            val capability = try {
                engine.capability()
            } catch (error: Exception) {
                capabilityText.text = "Terminal check failed: " + (error.message ?: "unknown error")
                return@launch
            }
            capabilityText.text = if (capability.pythonRuns) {
                capability.detail + "  (python: " + capability.pythonVersion + ", rembg: " +
                    (if (capability.rembgInstalled) "yes" else "no") + ", onnxruntime: " +
                    (if (capability.onnxInstalled) "yes" else "no") + ")"
            } else {
                capability.detail
            }
        }
    }

    private fun testHost() {
        val raw = hostField.text.toString()
        if (HostRemovalClient.isPlainHttpHost(raw)) {
            statusText.text = BgRemovalErrors.PLAIN_HTTP_BLOCKED
            return
        }
        config.hostUrl = raw
        val host = HostRemovalClient.normalizeHost(raw)
        if (host == null) {
            statusText.text = BgRemovalErrors.HOST_NOT_CONFIGURED
            return
        }
        statusText.text = "Testing " + host + " ..."
        lifecycleScope.launch {
            val outcome = withContext(Dispatchers.IO) { engine.pingCurrentHost() }
            statusText.text = outcome.message ?: "No answer from the host."
            planText.text = engine.plan().note
        }
    }

    private fun startRemoval() {
        val input = selected
        if (input == null) {
            statusText.text = "Select an image first."
            return
        }
        val plan = engine.plan()
        planText.text = plan.note
        if (plan.mode == RemovalMode.UNAVAILABLE) {
            statusText.text = plan.note
            hostField.requestFocus()
            return
        }
        job?.cancel()
        setBusy(true)
        resultText.text = ""
        statusText.text = "Removing the background..."
        job = lifecycleScope.launch {
            val outcome = try {
                engine.remove(input, plan)
            } catch (error: OutOfMemoryError) {
                RemovalResult.Failure(BgRemovalErrors.OUT_OF_MEMORY, plan)
            } catch (error: Exception) {
                RemovalResult.Failure(error.message ?: BgRemovalErrors.HOST_ERROR, plan)
            }
            setBusy(false)
            when (outcome) {
                is RemovalResult.Success -> {
                    resultFile = outcome.pngFile
                    showAfter(outcome.pngFile)
                    saveButton.isEnabled = true
                    retryButton.isEnabled = true
                    statusText.text = "Background removed. Nothing left this device unless your host was remote."
                    resultText.text = if (outcome.alphaVerified) {
                        "Verified transparent PNG (" + (outcome.transparentRatio * 100).toInt() + "% of sampled pixels are see-through) · " + outcome.plan.note
                    } else {
                        BgRemovalErrors.TOO_BIG_TO_CHECK
                    }
                }
                is RemovalResult.Failure -> {
                    statusText.text = outcome.message
                    resultText.text = ""
                    saveButton.isEnabled = false
                    retryButton.isEnabled = selected != null
                }
                is RemovalResult.Cancelled -> statusText.text = BgRemovalErrors.CANCELLED
            }
        }
    }

    private fun saveResult() {
        val png = resultFile ?: return
        val name = selected?.displayName ?: "photo.png"
        statusText.text = try {
            engine.saveToGallery(png, name)
        } catch (error: Exception) {
            BgRemovalErrors.SAVE_FAILED + " " + (error.message ?: "")
        }
        resultText.text = "Original image untouched."
    }

    private fun cyclePreview() {
        viewMode = when (viewMode) {
            MODE_BOTH -> if (resultFile != null) MODE_AFTER else MODE_BEFORE
            MODE_AFTER -> MODE_BEFORE
            else -> if (resultFile != null) MODE_BOTH else MODE_BEFORE
        }
        applyViewMode()
    }

    private fun applyViewMode() {
        beforeView.visibility = if (viewMode == MODE_AFTER) View.GONE else View.VISIBLE
        afterView.visibility = if (viewMode == MODE_BEFORE) View.GONE else View.VISIBLE
        previewButton.text = when (viewMode) {
            MODE_BOTH -> "Preview: both"
            MODE_AFTER -> "Preview: result"
            else -> "Preview: original"
        }
    }

    private fun showBefore() {
        val input = selected ?: return
        viewMode = MODE_BEFORE
        try {
            contentResolver.openInputStream(input.uri)?.use { stream ->
                beforeView.setImageBitmap(BitmapFactory.decodeStream(stream))
            }
        } catch (error: OutOfMemoryError) {
            statusText.text = BgRemovalErrors.OUT_OF_MEMORY
        } catch (error: Exception) {
            statusText.text = BgRemovalErrors.BAD_IMAGE
        }
        applyViewMode()
    }

    private fun showAfter(png: File) {
        try {
            afterView.setImageBitmap(BitmapFactory.decodeFile(png.absolutePath))
        } catch (error: OutOfMemoryError) {
            statusText.text = BgRemovalErrors.OUT_OF_MEMORY
        }
        viewMode = if (beforeView.drawable == null) MODE_AFTER else MODE_BOTH
        applyViewMode()
    }

    private fun setBusy(busy: Boolean) {
        progress.visibility = if (busy) View.VISIBLE else View.GONE
        removeButton.isEnabled = !busy && selected != null
        cancelButton.isEnabled = busy
        retryButton.isEnabled = !busy && selected != null && resultFile != null
    }

    private fun readableSize(bytes: Long): String {
        if (bytes <= 0) return "size unknown"
        val kb = bytes / 1024.0
        return if (kb < 1024) kb.toInt().toString() + " KB" else String.format("%.1f MB", kb / 1024.0)
    }

    companion object {
        private const val MODE_BOTH = 0
        private const val MODE_AFTER = 1
        private const val MODE_BEFORE = 2
    }
}
