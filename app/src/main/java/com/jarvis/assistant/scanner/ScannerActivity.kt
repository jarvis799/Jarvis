package com.jarvis.assistant.scanner

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.common.Barcode
import java.util.concurrent.Executors

/**
 * DEV STYLE: Jarvis controls the scanner - the user just points the camera.
 * Every detected code is handed straight to Jarvis, which decides the action
 * (open links, save WiFi, answer questions). No manual button stack.
 */
class ScannerActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var statusText: TextView
    private lateinit var resultText: TextView
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private var scanning = true

    private val cameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startCamera()
        else { statusText.text = "Camera permission denied. Grant it in Android Settings to scan."; showNoCameraState() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        com.jarvis.assistant.util.ThemeManager.applyTheme(this)
        super.onCreate(savedInstanceState)
        buildUi()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) startCamera()
        else cameraPermission.launch(Manifest.permission.CAMERA)
    }

    private fun buildUi() {
        val density = resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(28), dp(20), dp(20))
            background = android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                intArrayOf(0xFF1A2E0A.toInt(), 0xFF2E4D12.toInt(), 0xFF4D7C0F.toInt())
            )
        }
        statusText = TextView(this).apply {
            textSize = 15f
            setTextColor(0xFFE8FBE0.toInt())
            text = "Point the camera at a QR code or barcode - Jarvis handles everything else."
            setPadding(dp(4), 0, dp(4), dp(12))
        }
        container.addView(statusText)
        previewView = PreviewView(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f).apply { bottomMargin = dp(14) }
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dp(20).toFloat()
                setColor(0xFF0A1407.toInt())
            }
            clipToOutline = true
        }
        container.addView(previewView)
        resultText = TextView(this).apply {
            textSize = 14f
            setTextIsSelectable(true)
            setTextColor(0xFFE8FBE0.toInt())
            setPadding(dp(14), dp(12), dp(14), dp(12))
            minLines = 2
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dp(16).toFloat()
                setColor(0x33FFFFFF)
            }
        }
        container.addView(resultText)
        container.addView(bottomBar())
        setContentView(container)
    }

    /** DEV STYLE: every Jarvis screen keeps the same bottom navigation. */
    private fun bottomBar(): LinearLayout {
        val density = resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER
            setPadding(0, dp(10), 0, dp(2))
        }
        fun addItem(label: String, active: Boolean, action: (() -> Unit)?) {
            bar.addView(TextView(this).apply {
                text = label
                textSize = 12f
                gravity = android.view.Gravity.CENTER
                typeface = if (active) android.graphics.Typeface.DEFAULT_BOLD else android.graphics.Typeface.DEFAULT
                setTextColor(if (active) 0xFFFFFFFF.toInt() else 0xFF6B7F3A.toInt())
                background = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = dp(16).toFloat()
                    setColor(if (active) 0x33FFFFFF else 0x00000000)
                }
                setPadding(dp(10), dp(8), dp(10), dp(8))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                if (action != null) setOnClickListener { action() }
            })
        }
        addItem("Home", false) { openTab(null) }
        addItem("Chat", false) { openTab("chat") }
        addItem("Scan", true, null)
        addItem("Settings", false) {
            startActivity(android.content.Intent(this, com.jarvis.assistant.ui.settings.SettingsActivity::class.java))
        }
        return bar
    }

    private fun openTab(tab: String?) {
        val intent = android.content.Intent(this, com.jarvis.assistant.ui.home.MainActivity::class.java)
        if (tab != null) intent.putExtra("open_tab", tab)
        startActivity(intent)
        finish()
    }

    private fun startCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            try {
                val provider = providerFuture.get()
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { it.setAnalyzer(cameraExecutor, BarcodeAnalyzer { barcode -> runOnUiThread { handleBarcode(barcode) } }) }
                provider.unbindAll()
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            } catch (e: Exception) {
                statusText.text = "Could not start camera: " + e.message
                showNoCameraState()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun handleBarcode(barcode: Barcode) {
        if (!scanning) return
        scanning = false
        val value = barcode.rawValue ?: barcode.displayValue ?: ""
        val type = when (barcode.valueType) {
            Barcode.TYPE_URL -> "URL"
            Barcode.TYPE_WIFI -> "WiFi"
            Barcode.TYPE_CONTACT_INFO -> "Contact"
            Barcode.TYPE_EMAIL -> "Email"
            Barcode.TYPE_PHONE -> "Phone"
            Barcode.TYPE_SMS -> "SMS"
            Barcode.TYPE_GEO -> "Location"
            Barcode.TYPE_CALENDAR_EVENT -> "Calendar"
            else -> "Text"
        }
        resultText.text = "[" + type + "] " + value
        // DEV STYLE: Jarvis controls the result - hand it over automatically.
        com.jarvis.assistant.background.JarvisConversationService.sendTextMessage(
            "Jarvis, ami QR/barcode scan korlam [" + type + "]: " + value + " - eta tomar control e. " +
                "Dorkar hole URL open koro, WiFi save koro, naki answer deo."
        )
        statusText.text = "Jarvis ke pathano hoyeche - take right action korbe. Abar scan korle ready."
        previewView.postDelayed({ scanning = true }, 2500)
    }

    private fun showNoCameraState() {
        previewView.setBackgroundResource(android.R.color.darker_gray)
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}
