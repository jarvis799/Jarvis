package com.jarvis.assistant.ui.chat

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import androidx.core.content.ContextCompat
import kotlin.math.PI
import kotlin.math.sin

/**
 * DEV STYLE: three pulsing dots shown under the conversation while Jarvis is working on a reply.
 * Without it a slow model looks like a dead app - the dots prove the turn is still running.
 */
class ChatThinkingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = resolveDotColor(context) }
    private val phase = floatArrayOf(0f)
    private var animator: ValueAnimator? = null

    private fun resolveDotColor(context: Context): Int {
        val value = TypedValue()
        val resolved = context.theme.resolveAttribute(android.R.attr.textColorPrimary, value, true)
        return when {
            !resolved -> Color.WHITE
            value.resourceId != 0 -> runCatching { ContextCompat.getColor(context, value.resourceId) }.getOrDefault(Color.WHITE)
            else -> value.data
        }
    }

    fun start() {
        if (animator != null) return
        animator = ValueAnimator.ofFloat(0f, (2 * PI).toFloat()).apply {
            duration = 1100L
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener {
                phase[0] = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    fun stop() {
        animator?.cancel()
        animator = null
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        start()
    }

    override fun onDetachedFromWindow() {
        stop()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val radius = dp(3f)
        val gap = dp(11f)
        val centerY = height / 2f
        val totalWidth = gap * 2
        val startX = width / 2f - totalWidth / 2f
        for (index in 0 until 3) {
            val wave = sin(phase[0] + index * 0.9f).toFloat()
            paint.alpha = (110 + 145 * ((wave + 1f) / 2f)).toInt().coerceIn(0, 255)
            canvas.drawCircle(startX + index * gap, centerY - wave * dp(2f), radius, paint)
        }
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
}