package com.jarvis.assistant.ui.settings

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import com.jarvis.assistant.JarvisApp
import com.jarvis.assistant.databinding.ActivityOrbDensityBinding
import com.jarvis.assistant.util.OrbDensity
import com.jarvis.assistant.util.ThemeManager

/** DEV STYLE: a live orb density slider - light for weaker phones, dense for powerful ones. */
class OrbDensityActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOrbDensityBinding
    private val preferences get() = JarvisApp.instance.preferences
    private var particles = OrbDensity.DEFAULT_PARTICLES

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyTheme(this)
        super.onCreate(savedInstanceState)
        binding = ActivityOrbDensityBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ThemeManager.applyDynamicBackground(binding.root)

        particles = preferences.orbParticles
        binding.btnBack.setOnClickListener { finish() }
        binding.seekOrbDensity.max = 100
        binding.seekOrbDensity.progress = OrbDensity.percentFor(particles)
        showValue()

        binding.orbDensityPreview.apply {
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = true
            @Suppress("DEPRECATION")
            settings.allowFileAccessFromFileURLs = true
            @Suppress("DEPRECATION")
            settings.allowUniversalAccessFromFileURLs = true
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    applyToPreview()
                }
            }
            loadUrl(previewUrl())
        }

        binding.seekOrbDensity.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                particles = OrbDensity.particlesFor(progress)
                showValue()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                preferences.orbParticles = particles
                applyToPreview()
            }
        })
    }

    private fun showValue() {
        val percent = OrbDensity.percentFor(particles)
        binding.tvOrbDensityValue.text = percent.toString() + "% - " + particles + " particles"
        binding.tvOrbDensityHint.text = when {
            percent < 30 -> "Lightest: smoothest on older phones."
            percent > 75 -> "Densest: richest orb, needs a strong phone."
            else -> "Balanced detail and smoothness."
        }
    }

    private fun previewUrl(): String {
        return "file:///android_asset/orbs/orb-host.html?embedded=1&theme=" + preferences.appTheme +
            "&orb=jarvis1&particles=" + particles
    }

    private fun applyToPreview() {
        binding.orbDensityPreview.evaluateJavascript(
            "window.JarvisOrb && window.JarvisOrb.setParticles(" + particles + ")",
            null
        )
    }
}
