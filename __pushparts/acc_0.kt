package com.jarvis.assistant.automation.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.graphics.Bitmap
import android.view.Display
import java.io.ByteArrayOutputStream
import java.util.Locale
import java.util.concurrent.Executors
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class JarvisAccessibilityService : AccessibilityService() {

    data class UiChange(val revision: Long, val packageName: String, val eventType: Int, val timestamp: Long)

    companion object {
        private const val TAG = "JarvisAccessibility"
        private val _uiChanges = MutableStateFlow(UiChange(0L, "", 0, 0L))
        val uiChanges: StateFlow<UiChange> = _uiChanges.asStateFlow()

        @Volatile
        var instance: JarvisAccessibilityService? = null
            private set

        fun isRunning(): Boolean = instance != null
        fun isEnabled(): Boolean = instance != null

        fun triggerWhatsAppSend(targetText: String? = null) {
            instance?.armWhatsAppSend(targetText)
        }

        fun triggerWhatsAppCall(isVideo: Boolean) {
            instance?.armWhatsAppCall(isVideo)
        }

        fun triggerYouTubeAutoPlay() {
            instance?.armYouTubePlay()
        }

        fun triggerYouTubeSubscribe() {
            instance?.armYouTubeSubscribe()
        }

        fun triggerYouTubeFullscreen() {
            instance?.armYouTubeFullscreen()
        }

        fun triggerWhatsAppDelete(forEveryone: Boolean) {
            instance?.armWhatsAppDelete(forEveryone)
        }

        data class ExtractedOtp(val code: String, val timestamp: Long, val senderPkg: String)
        @Volatile
        var latestExtractedOtp: ExtractedOtp? = null

        fun getRecentOtp(): String? {
            val otp = latestExtractedOtp ?: return null
            if (System.currentTimeMillis() - otp.timestamp < 120_000L) { // valid for 2 minutes
                return otp.code
            }
            return null
        }

        suspend fun captureScreenBitmap(): Bitmap? {
            val s = instance ?: return null
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
            return try {
                withTimeoutOrNull(1500L) {
                    suspendCancellableCoroutine { cont ->
                        s.takeScreenCapture { bmp ->
                            if (cont.isActive) {
                                cont.resume(bmp, null)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "captureScreenBitmap timed out or failed: ${e.message}")
                null
            }
        }

        suspend fun captureScreenJpeg(quality: Int = 75): ByteArray? {
            // FAST PATH: If ScreenCaptureManager VirtualDisplay is running, grab immediate hardware frame (0ms latency)
            val scm = com.jarvis.assistant.vision.ScreenCaptureManager.getInstance()
            if (scm.isScreenShareActive()) {
                val fastSnap = scm.captureSnapshotJpeg(quality)
                if (fastSnap != null) return fastSnap
            }

            val bmp = captureScreenBitmap()
            if (bmp != null) {
                try {
                    val maxDim = 640f
                    val scale = (maxDim / maxOf(bmp.width, bmp.height)).coerceAtMost(1.0f)
                    val targetW = (bmp.width * scale).toInt() and 1.inv()
                    val targetH = (bmp.height * scale).toInt() and 1.inv()
                    val scaledBmp = if (scale < 1.0f && targetW > 0 && targetH > 0) {
                        Bitmap.createScaledBitmap(bmp, targetW, targetH, true)
                    } else {
                        bmp
                    }
                    val stream = ByteArrayOutputStream()
                    scaledBmp.compress(Bitmap.CompressFormat.JPEG, quality, stream)
                    if (scaledBmp != bmp) scaledBmp.recycle()
                    bmp.recycle()
                    val bytes = stream.toByteArray()
                    Log.d(TAG, "Captured native accessibility screenshot: ${bytes.size / 1024} KB (${targetW}x${targetH})")
                    return bytes
                } catch (e: Exception) {
                    Log.e(TAG, "Failed encoding accessibility screenshot to JPEG: ${e.message}")
                }
            }
            // Fallback to ScreenCaptureManager if active
            return com.jarvis.assistant.vision.ScreenCaptureManager.getInstance().captureSnapshotJpeg(quality)
        }
    }

    /**
     * Native Android 11+ (API 30+) AccessibilityService screen capture.
     * Takes a hardware buffer screenshot with ZERO user prompts or system dialogs.
     */
    fun takeScreenCapture(callback: (Bitmap?) -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                takeScreenshot(
                    Display.DEFAULT_DISPLAY,
                    Executors.newSingleThreadExecutor(),
                    object : AccessibilityService.TakeScreenshotCallback {
                        override fun onSuccess(screenshotResult: AccessibilityService.ScreenshotResult) {
                            try {
                                val buffer = screenshotResult.hardwareBuffer
                                val colorSpace = screenshotResult.colorSpace
                                val hwBitmap = Bitmap.wrapHardwareBuffer(buffer, colorSpace)
                                val softBitmap = hwBitmap?.copy(Bitmap.Config.ARGB_8888, false)
                                buffer.close()
                                hwBitmap?.recycle()
                                callback(softBitmap)
                            } catch (e: Exception) {
                                Log.e(TAG, "Error wrapping hardware buffer screenshot: ${e.message}")
                                callback(null)
                            }
                        }

                        override fun onFailure(errorCode: Int) {
                            Log.w(TAG, "takeScreenshot failed with errorCode: $errorCode")
                            callback(null)
                        }
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "takeScreenshot exception: ${e.message}")
                callback(null)
            }
        } else {
            callback(null)
        }
    }

    private val handler = Handler(Looper.getMainLooper())

    private var isWaitingForWhatsAppSend = false
    private var pendingWhatsAppMessage: String? = null
    private var whatsAppArmTime = 0L
    private var whatsAppSendTaps = 0

    private var isWaitingForWhatsAppCall = false
    private var isVideoCall = false
    private var whatsAppCallArmTime = 0L
    private var whatsAppCallTaps = 0

    private var isWaitingForYouTubePlay = false
    private var youTubeArmTime = 0L

    private var isWaitingForYouTubeSubscribe = false
    private var youTubeSubscribeArmTime = 0L

    private var isWaitingForYouTubeFullscreen = false
    private var youTubeFullscreenArmTime = 0L

    private var isWaitingForWhatsAppDelete = false
    private var deleteForEveryone = false
    private var whatsAppDeleteArmTime = 0L
    private var deleteStage = 0

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i(TAG, "JarvisAccessibilityService connected and active")
        ensureJarvisServiceActive()
    }

    private fun ensureJarvisServiceActive() {
        try {
            if (!com.jarvis.assistant.background.JarvisConversationService.isSessionOn.value &&
                !com.jarvis.assistant.background.JarvisConversationService.isStandby.value) {
                val intent = Intent(this, com.jarvis.assistant.background.JarvisConversationService::class.java).apply {
                    action = com.jarvis.assistant.background.JarvisConversationService.ACTION_ENTER_STANDBY
                }
                androidx.core.content.ContextCompat.startForegroundService(this, intent)
                Log.i(TAG, "JarvisAccessibilityService ensured JarvisConversationService is running in standby")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start JarvisConversationService from accessibility service: ${e.message}")
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val pkg = event.packageName?.toString().orEmpty()
        if (event.eventType in setOf(
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
                AccessibilityEvent.TYPE_WINDOWS_CHANGED,
                AccessibilityEvent.TYPE_VIEW_SCROLLED
            )) {
            val previous = _uiChanges.value
            _uiChanges.value = UiChange(previous.revision + 1, pkg, event.eventType, System.currentTimeMillis())
        }

        // DEV STYLE: premium floating media HUD - one-tap controls over the YouTube app.
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            com.jarvis.assistant.automation.accessibility.MediaHud.onWindowChanged(this, pkg)
        }

        // Auto-extract incoming OTP / verification codes from notifications (SMS, WhatsApp, banking)
        if (event.eventType == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {
            try {
                val texts = event.text
                val fullText = texts.joinToString(" ")
                if (fullText.isNotBlank()) {
                    val otpRegex = Regex("""\b(?:OTP|code|verification|passcode|is|pin|password)[^\d]*(\d{4,8})\b""", RegexOption.IGNORE_CASE)
                    val match = otpRegex.find(fullText) ?: Regex("""\b(\d{4,6})\b""").find(fullText)
                    if (match != null) {
                        val code = match.groupValues.lastOrNull { it.isNotBlank() && it.all { c -> c.isDigit() } }
                        if (code != null && code.length in 4..8) {
                            Log.i(TAG, "Auto-detected incoming OTP/code from notification ($pkg): $code")
                            latestExtractedOtp = ExtractedOtp(code, System.currentTimeMillis(), pkg)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "Error inspecting notification for OTP: ${e.message}")
            }
        }

        if (isWaitingForWhatsAppSend && (pkg == "com.whatsapp" || pkg == "com.whatsapp.w4b")) {
            if (System.currentTimeMillis() - whatsAppArmTime < 14000) {
                attemptWhatsAppSendAction(0)
            } else {
                isWaitingForWhatsAppSend = false
            }
        }

        if (isWaitingForWhatsAppCall && (pkg == "com.whatsapp" || pkg == "com.whatsapp.w4b")) {
            if (System.currentTimeMillis() - whatsAppCallArmTime < 14000) {
                attemptWhatsAppCallAction(0)
            } else {
                isWaitingForWhatsAppCall = false
            }
        }

        if (isWaitingForYouTubePlay && (pkg == "com.google.android.youtube" || pkg == "app.revanced.android.youtube")) {
            if (System.currentTimeMillis() - youTubeArmTime < 10000) {
                attemptClickFirstYouTubeVideo(0)
            } else {
                isWaitingForYouTubePlay = false
            }
        }

        if (isWaitingForYouTubeSubscribe && (pkg == "com.google.android.youtube" || pkg == "app.revanced.android.youtube")) {
            if (System.currentTimeMillis() - youTubeSubscribeArmTime < 10000) {
                attemptYouTubeSubscribe()
            } else {
                isWaitingForYouTubeSubscribe = false
            }
        }

        if (isWaitingForYouTubeFullscreen && (pkg == "com.google.android.youtube" || pkg == "app.revanced.android.youtube")) {
            if (System.currentTimeMillis() - youTubeFullscreenArmTime < 8000) {
                attemptYouTubeFullscreen(1)
            } else {
                isWaitingForYouTubeFullscreen = false
            }
        }

        if (isWaitingForWhatsAppDelete && (pkg == "com.whatsapp" || pkg == "com.whatsapp.w4b")) {
            if (System.currentTimeMillis() - whatsAppDeleteArmTime < 15000) {
                attemptWhatsAppDeleteAction()
            } else {
                isWaitingForWhatsAppDelete = false
            }
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "JarvisAccessibilityService interrupted")
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    // -------------------------------------------------------------
    // WhatsApp Send & Text Injection
    // -------------------------------------------------------------

    fun armWhatsAppSend(targetText: String?) {
        isWaitingForWhatsAppSend = true
        pendingWhatsAppMessage = targetText
        whatsAppArmTime = System.currentTimeMillis()
        whatsAppSendTaps = 0
        Log.i(TAG, "Armed for WhatsApp auto-send (has text=${!targetText.isNullOrBlank()})")

        // Rapid polling to catch WhatsApp UI as soon as it renders
        handler.postDelayed({ pollWhatsAppSend(1) }, 600)
    }

    private fun pollWhatsAppSend(attempt: Int) {
        if (!isWaitingForWhatsAppSend || attempt > 12) {
            isWaitingForWhatsAppSend = false
            pendingWhatsAppMessage = null
            return
        }

        val success = attemptWhatsAppSendAction(attempt)
        if (success && whatsAppSendTaps >= 1) {
            // Check if message sent and entry is cleared
            val root = getActiveRootNode()
            val entryText = root?.let { getWhatsAppEntryText(it) }
            if (entryText.isNullOrBlank()) {
                Log.i(TAG, "WhatsApp message sent! Entry field cleared. Ending auto-send polling.")
                isWaitingForWhatsAppSend = false
                pendingWhatsAppMessage = null
                return
            }
        }

        val delay = if (attempt < 3) 400L else 600L
        handler.postDelayed({ pollWhatsAppSend(attempt + 1) }, delay)
    }

    private fun attemptWhatsAppSendAction(attempt: Int): Boolean {
        val rootNode = getActiveRootNode() ?: return false

        val currentEntryText = getWhatsAppEntryText(rootNode)

        // If we already tapped send once:
        if (whatsAppSendTaps >= 1) {
            if (currentEntryText.isNullOrBlank()) {
                Log.i(TAG, "WhatsApp message sent successfully (entry is empty). Disarming.")
                isWaitingForWhatsAppSend = false
                pendingWhatsAppMessage = null
                return true
            }
            // If entry is not empty yet, wait for WhatsApp to process. DO NOT tap mic.
            Log.d(TAG, "Send already tapped once, waiting for entry to clear (attempt $attempt)")
            return false
        }

        // 1. Text injection check: if text field is empty and we have a message, inject it
        if (currentEntryText.isNullOrBlank()) {
            injectTextIfEmpty(rootNode)
        }

        // Ensure entry actually has text before attempting to cli