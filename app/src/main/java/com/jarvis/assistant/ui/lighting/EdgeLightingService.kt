package com.jarvis.assistant.ui.lighting

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import com.jarvis.assistant.JarvisApp
import com.jarvis.assistant.background.JarvisConversationService
import com.jarvis.assistant.ui.voice.VoiceOverlayState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * DEV STYLE: shows the four-sided animated edge lighting above any screen - including other
 * apps - through a non touchable, non focusable TYPE_APPLICATION_OVERLAY window. The glow
 * fades in on wake, follows the live conversation state and fades out once Jarvis is idle.
 */
class EdgeLightingService : Service() {

    companion object {
        const val ACTION_SHOW = "com.jarvis.assistant.EDGE_LIGHTING_SHOW"
        const val ACTION_STOP = "com.jarvis.assistant.EDGE_LIGHTING_STOP"

        /** How long the light keeps flowing after Jarvis goes idle before it fades out. */
        var idleTimeoutMs = 9000L

        private const val FADE_IN_MS = 420L
        private const val FADE_OUT_MS = 480L

        /** Launch the glow when the setting is on and overlay permission is granted. */
        fun show(context: Context) {
            val app = context.applicationContext
            if (app !is JarvisApp) return
            if (!app.preferences.isEdgeLightingEnabled) return
            if (!Settings.canDrawOverlays(app)) return
            try {
                app.startService(Intent(app, EdgeLightingService::class.java).setAction(ACTION_SHOW))
            } catch (_: Exception) {
            }
        }

        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, EdgeLightingService::class.java))
            } catch (_: Exception) {
            }
        }
    }

    private var windowManager: WindowManager? = null
    private var lightingView: EdgeLightingView? = null
    private var added = false
    private var handler: Handler? = null
    private var scope: CoroutineScope? = null
    private var stopping = false

    private val idleFade = Runnable { fadeOut() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            removeAndStop()
            return START_NOT_STICKY
        }
        if (added) {
            onWake()
            return START_NOT_STICKY
        }
        showGlow()
        return START_NOT_STICKY
    }

    private fun showGlow() {
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }
        val app = application as? JarvisApp
        if (app != null && !app.preferences.isEdgeLightingEnabled) {
            stopSelf()
            return
        }
        val wm = getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: run {
            stopSelf()
            return
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        val view = EdgeLightingView(this).apply {
            alpha = 0f
            setReducedMotion(reducedMotionEnabled())
            setState(VoiceOverlayState.WAKE)
        }
        try {
            wm.addView(view, params)
        } catch (_: Exception) {
            stopSelf()
            return
        }
        added = true
        windowManager = wm
        lightingView = view
        handler = Handler(Looper.getMainLooper())
        view.setRunning(true)
        view.animate().alpha(1f).setDuration(FADE_IN_MS).start()
        observeConversation()
    }

    private fun observeConversation() {
        val current = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
        scope = current
        current.launch {
            JarvisConversationService.conversationState.collect { state ->
                applyState(VoiceOverlayState.from(state))
            }
        }
        current.launch {
            JarvisConversationService.audioLevel.collect { level -> lightingView?.setAmplitude(level) }
        }
        current.launch {
            JarvisConversationService.wakeEvents.collect { onWake() }
        }
    }

    private fun applyState(state: VoiceOverlayState) {
        val view = lightingView ?: return
        view.setState(state)
        if (state == VoiceOverlayState.IDLE) {
            scheduleIdleFade()
        } else {
            handler?.removeCallbacks(idleFade)
            view.setRunning(true)
            if (view.alpha < 1f) {
                view.animate().alpha(1f).setDuration(FADE_IN_MS).start()
            }
        }
    }

    private fun onWake() {
        val view = lightingView ?: return
        handler?.removeCallbacks(idleFade)
        view.setState(VoiceOverlayState.WAKE)
        view.setRunning(true)
        view.animate().alpha(1f).setDuration(FADE_IN_MS).start()
    }

    private fun scheduleIdleFade() {
        val h = handler ?: return
        h.removeCallbacks(idleFade)
        h.postDelayed(idleFade, idleTimeoutMs)
    }

    private fun fadeOut() {
        val view = lightingView
        if (view == null) {
            removeAndStop()
            return
        }
        view.setRunning(false)
        view.animate().alpha(0f).setDuration(FADE_OUT_MS).withEndAction { removeAndStop() }.start()
    }

    private fun removeAndStop() {
        if (stopping) return
        stopping = true
        scope?.cancel()
        scope = null
        handler?.removeCallbacksAndMessages(null)
        handler = null
        if (added) {
            try {
                windowManager?.removeView(lightingView)
            } catch (_: Exception) {
            }
        }
        added = false
        lightingView = null
        windowManager = null
        stopSelf()
    }

    private fun reducedMotionEnabled(): Boolean {
        val scale = Settings.Global.getFloat(
            contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        )
        return scale == 0f
    }

    override fun onDestroy() {
        removeAndStop()
        super.onDestroy()
    }
}
