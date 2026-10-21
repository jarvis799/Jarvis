package com.jarvis.assistant.ui.lighting

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.os.SystemClock
import android.view.View
import com.jarvis.assistant.ui.voice.VoiceOverlayGlass
import com.jarvis.assistant.ui.voice.VoiceOverlayState
import kotlin.math.sin

/**
 * DEV STYLE: four-sided animated edge lighting. A thin luminous rounded-rect outline hugs
 * every screen edge while cyan, electric blue, violet and magenta light flows around it,
 * with a soft outer bloom from stacked translucent strokes. Pure hardware rendering at
 * about 30 FPS, frozen when motion is reduced.
 */
class EdgeLightingView(context: Context) : View(context) {

    companion object {
        // DEV STYLE: warm orange melting into magenta, violet, blue and cyan - like the reference.
        val SPECTRUM = intArrayOf(
            0xFFFF6A1A.toInt(),
            0xFFE91E63.toInt(),
            VoiceOverlayGlass.MAGENTA,
            VoiceOverlayGlass.VIOLET,
            0xFF2F6BFF.toInt(),
            VoiceOverlayGlass.CYAN,
            0xFF19D2E8.toInt(),
            0xFFFF6A1A.toInt()
        )
        private const val FRAME_MS = 33L
    }

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private var phase = 0f
    private var lastFrame = 0L
    private var running = false
    private var state = VoiceOverlayState.IDLE
    private var amplitude = 0f
    private var reducedMotion = false

    /** Call with true to animate, false to freeze (during fade-out). */
    fun setRunning(run: Boolean) {
        running = run
        if (run) {
            lastFrame = 0L
            postInvalidateOnAnimation()
        }
    }

    fun setState(next: VoiceOverlayState) {
        if (state != next) {
            state = next
            postInvalidateOnAnimation()
        }
    }

    /** Voice level between 0 and 1, used for the gentle speaking pulses. */
    fun setAmplitude(level: Float) {
        amplitude = level.coerceIn(0f, 1f)
    }

    fun setReducedMotion(reduced: Boolean) {
        reducedMotion = reduced
        postInvalidateOnAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val cx = w / 2f
        val cy = h / 2f
        val inset = -dp(26f)
        val ring = RectF(inset, inset, w - inset, h - inset)
        val corner = dp(46f)

        val matrix = Matrix()
        matrix.postRotate(phase, cx, cy)
        val span = (w + h) * 0.5f
        val shader = LinearGradient(
            cx - span, cy, cx + span, cy,
            SPECTRUM, null, Shader.TileMode.MIRROR
        )
        shader.setLocalMatrix(matrix)
        ringPaint.shader = shader

        val brightness = brightnessFor(state) * if (reducedMotion) 0.72f else 1f
        val wave = (sin(SystemClock.uptimeMillis() / 1000f * 3.1f) * 0.5f + 0.5f)
        val pulse = when {
            state == VoiceOverlayState.SPEAKING && !reducedMotion -> 1f + (0.12f + amplitude * 0.34f) * wave
            !reducedMotion -> 1f + 0.05f * wave
            else -> 1f
        }

        val layers = arrayOf(
            floatArrayOf(dp(156f), 0.16f),
            floatArrayOf(dp(108f), 0.24f),
            floatArrayOf(dp(74f), 0.34f),
            floatArrayOf(dp(50f), 0.46f),
            floatArrayOf(dp(32f), 0.58f),
            floatArrayOf(dp(18f), 0.74f),
            floatArrayOf(dp(7f), 1.00f)
        )
        for (layer in layers) {
            ringPaint.strokeWidth = layer[0] * pulse
            ringPaint.alpha = (255f * layer[1] * brightness).toInt().coerceIn(0, 255)
            canvas.drawRoundRect(ring, corner, corner, ringPaint)
        }

        if (running) {
            val now = SystemClock.uptimeMillis()
            if (lastFrame == 0L || now - lastFrame >= FRAME_MS) {
                if (!reducedMotion) {
                    phase = (phase + speedFor(state)) % 360f
                }
                lastFrame = now
            }
            postInvalidateOnAnimation()
        }
    }

    private fun speedFor(value: VoiceOverlayState): Float = when (value) {
        VoiceOverlayState.WAKE -> 2.2f
        VoiceOverlayState.LISTENING -> 0.9f
        VoiceOverlayState.PROCESSING -> 1.6f
        VoiceOverlayState.SPEAKING -> 0.6f
        VoiceOverlayState.IDLE -> 0.35f
    }

    private fun brightnessFor(value: VoiceOverlayState): Float = when (value) {
        VoiceOverlayState.WAKE -> 1.38f
        VoiceOverlayState.LISTENING -> 1.16f
        VoiceOverlayState.PROCESSING -> 1.06f
        VoiceOverlayState.SPEAKING -> 1.26f
        VoiceOverlayState.IDLE -> 0.84f
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
}
