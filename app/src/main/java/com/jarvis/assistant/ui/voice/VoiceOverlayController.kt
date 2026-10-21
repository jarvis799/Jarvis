package com.jarvis.assistant.ui.voice

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.jarvis.assistant.JarvisApp
import com.jarvis.assistant.background.JarvisConversationService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * DEV STYLE: hosts the glass voice overlay in a single TYPE_APPLICATION_OVERLAY window, so
 * it appears above any app without launching an activity. Only one instance can exist at a
 * time and every collector, window and animation is released when the overlay is dismissed.
 */
object VoiceOverlayController {

    private var panel: VoiceOverlayPanel? = null
    private var windowManager: WindowManager? = null
    private var scope: CoroutineScope? = null
    private var forcedMicMute = false
    private var micMutedBefore = false

    fun isVisible(): Boolean = panel != null

    fun show(context: Context) {
        val app = context.applicationContext
        if (Looper.myLooper() != Looper.getMainLooper()) {
            Handler(Looper.getMainLooper()).post { show(app) }
            return
        }
        if (panel != null) return
        if (!Settings.canDrawOverlays(app)) return
        val jarvis = app as? JarvisApp ?: return
        val manager = app.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
        val preferences = jarvis.preferences

        val view = VoiceOverlayPanel(
            app,
            false,
            onSend = { text -> sendTyped(text) },
            onMicToggle = { toggleMic() },
            onClose = { hide() }
        )
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        try {
            manager.addView(view, params)
        } catch (_: Exception) {
            return
        }
        panel = view
        windowManager = manager
        micMutedBefore = preferences.isMicMuted
        forcedMicMute = !preferences.isVoiceOverlayAutoListen
        if (forcedMicMute) {
            preferences.isMicMuted = true
            JarvisConversationService.setMicMuted(true)
        }
        ViewCompat.setOnApplyWindowInsetsListener(view) { target, insets ->
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            (target as? VoiceOverlayPanel)?.applyInsets(ime, bars.top)
            insets
        }
        view.alpha = 0f
        view.animate().alpha(1f).setDuration(220L).start()
        startObserving()
    }

    fun hide() {
        val view = panel ?: return
        scope?.cancel()
        scope = null
        val jarvis = view.context.applicationContext as? JarvisApp
        if (forcedMicMute && jarvis != null) {
            jarvis.preferences.isMicMuted = micMutedBefore
            JarvisConversationService.setMicMuted(micMutedBefore)
        }
        forcedMicMute = false
        try {
            view.dismissKeyboard()
        } catch (_: Exception) {
        }
        try {
            windowManager?.removeView(view)
        } catch (_: Exception) {
        }
        panel = null
        windowManager = null
    }

    private fun sendTyped(text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        val view = panel ?: return
        view.appendTyped(clean)
        JarvisConversationService.sendTextMessage(clean)
    }

    private fun toggleMic() {
        val jarvis = panel?.context?.applicationContext as? JarvisApp ?: return
        val unmute = JarvisConversationService.micMutedState.value
        JarvisConversationService.setMicMuted(unmute)
        jarvis.preferences.isMicMuted = unmute
        forcedMicMute = false
        if (unmute && !JarvisConversationService.isServiceRunning()) {
            try {
                jarvis.startService(
                    Intent(jarvis, JarvisConversationService::class.java)
                        .setAction(JarvisConversationService.ACTION_START_CONVERSATION)
                )
            } catch (_: Exception) {
            }
        }
    }

    private fun startObserving() {
        val current = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
        scope = current
        current.launch {
            JarvisConversationService.conversationState.collect { state ->
                panel?.renderState(VoiceOverlayState.from(state))
            }
        }
        current.launch {
            JarvisConversationService.liveUserTranscript.collect { text -> panel?.setTranscript(text) }
        }
        current.launch {
            JarvisConversationService.liveAssistantResponse.collect { text -> panel?.setResponse(text) }
        }
        current.launch {
            JarvisConversationService.micMutedState.collect { muted -> panel?.renderMic(muted) }
        }
        current.launch {
            JarvisConversationService.wakeEvents.collect { panel?.flashWake() }
        }
    }
}
