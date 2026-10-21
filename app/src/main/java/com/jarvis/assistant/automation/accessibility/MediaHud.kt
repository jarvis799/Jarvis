package com.jarvis.assistant.automation.accessibility

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/**
 * DEV STYLE: premium floating media HUD. A glass pill with one-tap media
 * controls floats over the YouTube app, drawn by the accessibility service -
 * no overlay permission needed (TYPE_ACCESSIBILITY_OVERLAY).
 */
object MediaHud {

    private var view: LinearLayout? = null
    private val main = Handler(Looper.getMainLooper())

    fun onWindowChanged(service: AccessibilityService, pkg: String) {
        val ytPkg = pkg == "com.google.android.youtube" || pkg == "app.revanced.android.youtube" ||
            pkg == "com.google.android.apps.youtube.music"
        if (ytPkg) show(service) else hide()
    }

    private fun show(service: AccessibilityService) {
        if (view != null) return
        main.post {
            if (view != null) return@post
            try {
                val ctx = service
                val dp = ctx.resources.displayMetrics.density
                val pill = GradientDrawable().apply { setColor(0xE60E1420.toInt()); cornerRadius = 24f * dp }
                val row = LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    background = pill
                    setPadding((9 * dp).toInt(), (7 * dp).toInt(), (9 * dp).toInt(), (7 * dp).toInt())
                }

                fun chip(label: String, action: () -> Boolean) {
                    row.addView(TextView(ctx).apply {
                        text = label
                        textSize = 11.5f
                        typeface = Typeface.DEFAULT_BOLD
                        setTextColor(0xFFF4F7FF.toInt())
                        background = GradientDrawable().apply { setColor(0x2A22D3EE); cornerRadius = 18f * dp }
                        setPadding((12 * dp).toInt(), (7 * dp).toInt(), (12 * dp).toInt(), (7 * dp).toInt())
                        setOnClickListener {
                            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            val ok = runCatching { action() }.getOrDefault(false)
                            val msg = if (ok) "Jarvis: " + label + " done, Sir" else "Jarvis: " + label + " - not on this screen"
                            Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
                        }
                    })
                    row.addView(View(ctx).apply { layoutParams = LinearLayout.LayoutParams((7 * dp).toInt(), 1) })
                }

                chip("Skip Ad") { JarvisAccessibilityService.instance?.skipAd() == true }
                chip("Like") { JarvisAccessibilityService.instance?.likeVideo() == true }
                chip("Subscribe") { JarvisAccessibilityService.instance?.subscribeChannel() == true }
                chip("Fullscreen") {
                    if (JarvisAccessibilityService.instance == null) false
                    else runCatching { JarvisAccessibilityService.triggerYouTubeFullscreen() }.isSuccess
                }

                val lp = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT
                )
                lp.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                lp.y = (16 * dp).toInt()
                ctx.getSystemService(WindowManager::class.java)?.addView(row, lp)
                view = row
            } catch (_: Exception) {
                view = null
            }
        }
    }

    fun hide() {
        if (view == null) return
        main.post {
            val v = view ?: return@post
            view = null
            runCatching { v.context.getSystemService(WindowManager::class.java)?.removeViewImmediate(v) }
        }
    }
}
