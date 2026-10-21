package com.jarvis.assistant.runtime

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.TextView
import androidx.webkit.WebViewAssetLoader
import java.io.File

/**
 * Opens an installed web_tool feature. Content is served only from the
 * feature's private installed directory through WebViewAssetLoader - no file
 * or content access, no JS bridges, and external links open in the browser.
 */
class FeatureViewerActivity : Activity() {

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val featureId = intent.getStringExtra("feature_id")
        val feature = featureId?.let { FeatureRegistry.get(this, it) }
        if (feature == null) { finish(); return }

        val dir = File(FeatureRegistry.featureDir(this, feature.id), feature.version)
        val entry = feature.entry.ifBlank { "index.html" }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF070B14.toInt())
        }

        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(18), dp(16), dp(18), dp(12))
        }
        bar.addView(TextView(this).apply {
            text = "<"
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(0xFF22D3EE.toInt())
            setPadding(dp(6), 0, dp(14), 0)
            setOnClickListener { finish() }
        })
        bar.addView(TextView(this).apply {
            text = feature.name
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(0xFFF4F7FF.toInt())
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        root.addView(bar)
        root.addView(View(this).apply { setBackgroundColor(0x221D2B4A.toInt()) },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1))

        val marker = "feature/" + feature.id + "/"
        val assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/feature/", object : WebViewAssetLoader.PathHandler {
                override fun handle(path: String): WebResourceResponse? {
                    var relative = path.trim()
                    if (relative.startsWith("/")) relative = relative.removePrefix("/")
                    if (relative.startsWith(marker)) relative = relative.removePrefix(marker)
                    else if (relative.startsWith(feature.id + "/")) relative = relative.removePrefix(feature.id + "/")
                    if (relative.contains("..")) return null
                    val file = File(dir, relative)
                    if (!file.canonicalPath.startsWith(dir.canonicalPath + File.separator) || !file.isFile) return null
                    return WebResourceResponse(mimeOf(file), "utf-8", file.inputStream())
                }
            })
            .build()

        val web = WebView(this).apply {
            setBackgroundColor(0xFF070B14.toInt())
            settings.javaScriptEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.cacheMode = WebSettings.LOAD_NO_CACHE
            settings.mediaPlaybackRequiresUserGesture = true
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                    assetLoader.shouldInterceptRequest(request.url)
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val url = request.url
                    if (url.host == WebViewAssetLoader.DEFAULT_DOMAIN) return false
                    if (url.scheme == "http" || url.scheme == "https") {
                        startActivity(Intent(Intent.ACTION_VIEW, url).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                    return true
                }
            }
            loadUrl("https://" + WebViewAssetLoader.DEFAULT_DOMAIN + "/feature/" + feature.id + "/" + entry)
        }
        root.addView(web, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
    }

    private fun mimeOf(file: File): String = when (file.extension.lowercase()) {
        "html", "htm" -> "text/html"
        "css" -> "text/css"
        "js" -> "application/javascript"
        "svg" -> "image/svg+xml"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "json" -> "application/json"
        else -> "application/octet-stream"
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
