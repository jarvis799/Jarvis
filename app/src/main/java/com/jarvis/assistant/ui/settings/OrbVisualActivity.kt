package com.jarvis.assistant.ui.settings

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewClientCompat
import com.jarvis.assistant.JarvisApp
import com.jarvis.assistant.R
import com.jarvis.assistant.util.ThemeManager

/**
 * Premium orb picker: Speaking Orb (default), Voice Orb and Face Flow 3D.
 * The choice is stored in AppPreferences.orbStyle and picked up by HomeFragment.
 */
class OrbVisualActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var assetLoader: WebViewAssetLoader
    private val preferences get() = JarvisApp.instance.preferences

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyTheme(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_orb_visual)
        ThemeManager.applyDynamicBackground(findViewById(R.id.orbVisualRoot))

        findViewById<android.view.View>(R.id.btnBack).setOnClickListener { finish() }

        // Orb density lives inside the existing Orb Studio screen - no separate page.
        val densityBar = findViewById<android.widget.SeekBar>(R.id.seekOrbDensity)
        val densityValue = findViewById<android.widget.TextView>(R.id.tvOrbDensity)
        densityBar.progress = com.jarvis.assistant.util.OrbDensity.percentFor(preferences.orbParticles)
        densityValue.text = densityLabel(densityBar.progress)
        densityBar.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                densityValue.text = densityLabel(progress)
            }

            override fun onStartTrackingTouch(bar: android.widget.SeekBar?) = Unit

            override fun onStopTrackingTouch(bar: android.widget.SeekBar?) {
                preferences.orbParticles = com.jarvis.assistant.util.OrbDensity.particlesFor(bar?.progress ?: 0)
            }
        })

        webView = findViewById(R.id.orbPickerWebView)
        assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()
        webView.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.allowFileAccess = false
        webView.addJavascriptInterface(OrbVisualBridge(), "OrbVisualBridge")
        webView.webViewClient = object : WebViewClientCompat() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                assetLoader.shouldInterceptRequest(request.url)

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                pushSelection()
            }
        }
        webView.loadUrl(ASSET_URL)
    }

    private fun densityLabel(percent: Int): String =
        percent.toString() + "% • " + com.jarvis.assistant.util.OrbDensity.particlesFor(percent) + " particles"

    private fun pushSelection() {
        webView.evaluateJavascript("window.setCurrentOrb && window.setCurrentOrb('" + preferences.orbStyle + "')", null)
    }

    private inner class OrbVisualBridge {
        @JavascriptInterface
        fun pageReady() {
            runOnUiThread { pushSelection() }
        }

        @JavascriptInterface
        fun selectOrb(id: String) {
            runOnUiThread { preferences.orbStyle = id }
        }
    }

    override fun onDestroy() {
        if (::webView.isInitialized) {
            webView.removeJavascriptInterface("OrbVisualBridge")
            webView.stopLoading()
            webView.destroy()
        }
        super.onDestroy()
    }

    companion object {
        private const val ASSET_URL = "https://appassets.androidplatform.net/assets/orbs/picker.html"
    }
}
