package com.jarvis.assistant.ui.chat

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.SweepGradient
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator

/**
 * DEV STYLE: a light beam that travels around the chat area, so the conversation
 * surface reads as alive while Jarvis is listening or thinking.
 * Draws only; it is never clickable, so touches fall through to the chat list.
 */
class ChatBorderBeamView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.4f * density
        strokeCap = Paint.Cap.ROUND
    }
    private val bounds = RectF()
    private val rotation = Matrix()
    private var radius = 26f * density
    private var progress = 0f
    private var animator: ValueAnimator? = null

    private val beamColors = intArrayOf(
        Color.TRANSPARENT,
        Color.parseColor("#A8FF7A"),
        Color.parseColor("#5EEAD4"),
        Color.parseColor("#E6F766"),
        Color.TRANSPARENT
    )
    private val beamStops = floatArrayOf(0f, 0.16f, 0.30f, 0.44f, 0.60f)

    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val inset = paint.strokeWidth / 2f + density
        bounds.set(inset, inset, w - inset, h - inset)
        radius = (bounds.width().coerceAtMost(bounds.height()) * 0.06f)
            .coerceIn(16f * density, 30f * density)
    }

    override fun onDraw(canvas: Canvas) {
        if (bounds.width() <= 0f || bounds.height() <= 0f) return
        val cx = bounds.centerX()
        val cy = bounds.centerY()
        val shader = SweepGradient(cx, cy, beamColors, beamStops)
        rotation.setRotate(progress * 360f, cx, cy)
        shader.setLocalMatrix(rotation)
        paint.shader = shader
        canvas.drawRoundRect(bounds, radius, radius, paint)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startBeam()
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        animator = null
        super.onDetachedFromWindow()
    }

    private fun startBeam() {
        if (animator != null) return
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 3400L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                progress = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }
}