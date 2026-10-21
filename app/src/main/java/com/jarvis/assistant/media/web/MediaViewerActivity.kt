package com.jarvis.assistant.media.web

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast

/**
 * DEV STYLE: in-app media viewer - YouTube and Instagram render in a WebView
 * with premium chrome, so Jarvis keeps the user inside its own interface.
 */
@SuppressLint("SetJavaScriptEnabled")
class MediaViewerActivity : Activity() {

    private var webView: WebView? = null
    private var progress: ProgressBar? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val embed = intent.getStringExtra("embed")
        if (embed.isNullOrBlank()) { finish(); return }
        val title = intent.getStringExtra("title") ?: "Media"
        val page = intent.getStringExtra("page").orEmpty()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF070B14.toInt())
        }
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(10))
            setBackgroundColor(0xFF0B1120.toInt())
        }
        bar.addView(TextView(this).apply {
            text = "Back"
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(0xFF22D3EE.toInt())
            setPadding(dp(4), 0, dp(14), 0)
            setOnClickListener { finish() }
        })
        bar.addView(TextView(this).apply {
            text = title
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(0xFFF4F7FF.toInt())
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        if (page.isNotBlank()) {
            bar.addView(TextView(this).apply {
                text = "OPEN IN APP"
                textSize = 11f
                typeface = Typeface.DEFAULT_BOLD
                letterSpacing = 0.08f
                setTextColor(0xFF070B14.toInt())
                background = GradientDrawable().apply { setColor(0xFF22D3EE.toInt()); cornerRadius = dp(999f).toFloat() }
                setPadding(dp(12), dp(6), dp(12), dp(6))
                setOnClickListener {
                    runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(page))) }
                        .onFailure { Toast.makeText(this@MediaViewerActivity, "No app found for this link", Toast.LENGTH_SHORT).show() }
                }
            })
        }
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(3))
            progressTintList = ColorStateList.valueOf(0xFF22D3EE.toInt())
            max = 100
        }
        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.domStorageEnabled = true
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            settings.allowFileAccess = false
            setBackgroundColor(0xFF070B14.toInt())
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val host = request.url.host?.lowercase() ?: return false
                    val inApp = host.contains("youtube") || host.contains("youtu.be") || host.contains("ytimg") ||
                        host.contains("googlevideo") || host.contains("instagram") || host.contains("cdninstagram") ||
                        host.contains("fbcdn") || host.contains("google.com")
                    if (inApp) return false
                    runCatching { startActivity(Intent(Intent.ACTION_VIEW, request.url)) }
                    return true
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView, newProgress: Int) {
                    this@MediaViewerActivity.progress?.progress = newProgress
                    this@MediaViewerActivity.progress?.visibility = if (newProgress >= 100) View.GONE else View.VISIBLE
                }
            }
        }
        root.addView(bar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(progress)
        root.addView(webView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
        webView?.loadUrl(embed)
    }

    override fun onBackPressed() {
        if (webView?.canGoBack() == true) webView?.goBack() else super.onBackPressed()
    }

    override fun onDestroy() {
        webView?.apply {
            runCatching { loadUrl("about:blank") }
            runCatching { destroy() }
        }
        webView = null
        super.onDestroy()
    }

        private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun dp(v: Float): Int = (v * resources.displayMetrics.density).toInt()
}
