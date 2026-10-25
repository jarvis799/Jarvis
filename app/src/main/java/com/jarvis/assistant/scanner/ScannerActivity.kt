package com.jarvis.assistant.scanner

import android.Manifest
import android.animation.ObjectAnimator
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.jarvis.assistant.databinding.ActivityScannerBinding
import java.util.concurrent.Executors

/** Jarvis Scanner — point at any QR/barcode. Links auto-open, WiFi auto-connects, text is copied. */
class ScannerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityScannerBinding
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private var camera: Camera? = null
    private var scanning = true
    private var torchOn = false

    private val cameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startCamera() else Toast.makeText(this, "Camera permission needed to scan.", Toast.LENGTH_LONG).show()
    }
    private val galleryPicker = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scanFromUri(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        com.jarvis.assistant.util.ThemeManager.applyTheme(this)
        super.onCreate(savedInstanceState)
        binding = ActivityScannerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.btnBack.setOnClickListener { finish() }
        binding.btnFlash.setOnClickListener { setTorch(!torchOn) }
        binding.flashToggle.setOnCheckedChangeListener { _, checked -> setTorch(checked) }
        binding.btnGallery.setOnClickListener { galleryPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
        wireNav()
        startScanLineAnimation()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) startCamera()
        else cameraPermission.launch(Manifest.permission.CAMERA)
    }

    private fun wireNav() {
        binding.navHome.setOnClickListener { openTab(null) }
        binding.navChat.setOnClickListener { openTab("chat") }
        binding.navSettings.setOnClickListener { startActivity(Intent(this, com.jarvis.assistant.ui.settings.SettingsActivity::class.java)) }
    }

    private fun openTab(tab: String?) {
        val intent = Intent(this, com.jarvis.assistant.ui.home.MainActivity::class.java)
        if (tab != null) intent.putExtra("open_tab", tab)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        startActivity(intent)
        finish()
    }

    private fun startScanLineAnimation() {
        binding.scanFrame.post {
            val target = (binding.scanFrame.height - binding.scanLine.height).toFloat().coerceAtLeast(0f)
            ObjectAnimator.ofFloat(binding.scanLine, "translationY", 0f, target).apply {
                duration = 1600
                repeatMode = ObjectAnimator.REVERSE
                repeatCount = ObjectAnimator.INFINITE
                start()
            }
        }
    }

    private fun startCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            try {
                val provider = providerFuture.get()
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(binding.previewView.surfaceProvider) }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { it.setAnalyzer(cameraExecutor, BarcodeAnalyzer { b -> runOnUiThread { handleBarcode(b) } }) }
                provider.unbindAll()
                camera = provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                camera?.cameraControl?.enableTorch(torchOn)
            } catch (e: Exception) {
                Toast.makeText(this, "Could not start camera.", Toast.LENGTH_LONG).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun setTorch(on: Boolean) {
        torchOn = on
        binding.flashToggle.setOnCheckedChangeListener(null)
        binding.flashToggle.isChecked = on
        binding.flashToggle.setOnCheckedChangeListener { _, checked -> setTorch(checked) }
        camera?.cameraControl?.enableTorch(on)
    }

    private fun handleBarcode(barcode: Barcode) {
        if (!scanning) return
        scanning = false
        vibrate()
        when (barcode.valueType) {
            Barcode.TYPE_URL -> {
                val url = barcode.url?.url ?: barcode.rawValue ?: ""
                showResult("LINK  -  " + url)
                if (url.isNotBlank()) openUrl(url)
            }
            Barcode.TYPE_WIFI -> {
                val ssid = barcode.wifi?.ssid ?: ""
                val pass = barcode.wifi?.password ?: ""
                val enc = barcode.wifi?.encryptionType ?: 1
                showResult("WIFI  -  " + ssid)
                connectWifi(ssid, pass, enc)
            }
            else -> {
                val v = barcode.rawValue ?: barcode.displayValue ?: ""
                showResult("SCANNED  -  " + v)
                copyToClipboard(v)
            }
        }
        binding.scanFrame.postDelayed({ scanning = true; binding.resultText.isVisible = false }, 3000)
    }

    private fun openUrl(url: String) {
        var u = url.trim()
        if (!u.startsWith("http://") && !u.startsWith("https://")) u = "https://" + u
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            Toast.makeText(this, "No app to open this link.", Toast.LENGTH_LONG).show()
        }
    }

    @Suppress("DEPRECATION")
    private fun connectWifi(ssid: String, password: String, encryptionType: Int) {
        if (ssid.isBlank()) { Toast.makeText(this, "No WiFi name found in the code.", Toast.LENGTH_LONG).show(); return }
        val wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as? WifiManager
        if (wifiManager == null) { Toast.makeText(this, "WiFi manager unavailable.", Toast.LENGTH_LONG).show(); return }
        val conf = WifiConfiguration()
        conf.Ssid = '"' + ssid + '"'
        if (encryptionType != 1) conf.preSharedKey = '"' + password + '"'
        wifiManager.configuredNetworks.firstOrNull { it.Ssid == '"' + ssid + '"' }?.let { wifiManager.removeNetwork(it.networkId) }
        val netId = wifiManager.addNetwork(conf)
        if (netId == -1) { Toast.makeText(this, "Could not add " + ssid + ".", Toast.LENGTH_LONG).show(); return }
        wifiManager.disconnect()
        wifiManager.enableNetwork(netId, true)
        wifiManager.reconnect()
        Toast.makeText(this, "Connecting to " + ssid + " ...", Toast.LENGTH_LONG).show()
    }

    private fun scanFromUri(uri: Uri) {
        try {
            val image = InputImage.fromFilePath(this, uri)
            BarcodeScanning.getClient(BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS).build())
                .process(image)
                .addOnSuccessListener { barcodes -> runOnUiThread {
                    if (barcodes.isNotEmpty()) handleBarcode(barcodes.first())
                    else Toast.makeText(this, "No code found in that image.", Toast.LENGTH_LONG).show()
                } }
                .addOnFailureListener { Toast.makeText(this, "Could not read that image.", Toast.LENGTH_LONG).show() }
        } catch (e: Exception) {
            Toast.makeText(this, "Could not load that image.", Toast.LENGTH_LONG).show()
        }
    }

    private fun showResult(text: String) { binding.resultText.text = text; binding.resultText.isVisible = true }
    private fun copyToClipboard(text: String) {
        runCatching {
            val cb = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            cb.setPrimaryClip(ClipData.newPlainText("Jarvis Scan", text))
        }
    }
    private fun vibrate() {
        val v = getSystemService(VIBRATOR_SERVICE) as? Vibrator ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            v.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE))
        else @Suppress("DEPRECATION") v.vibrate(40)
    }

    override fun onDestroy() { super.onDestroy(); cameraExecutor.shutdown() }
}