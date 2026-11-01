package com.jarvis.assistant.lock

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View

class VoicePulse(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; strokeWidth = 3f; style = Paint.Style.STROKE }
    private var phase = 0f
    var level = 0f
        set(value) { field = value; invalidate() }
    private var animation: ValueAnimator? = null
    fun listening(active: Boolean) {
        animation?.cancel(); animation = null
        if (active) animation = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1500; repeatCount = ValueAnimator.INFINITE
            addUpdateListener { phase = it.animatedValue as Float; invalidate() }; start()
        } else { phase = 0f; level = 0f; invalidate() }
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val radius = minOf(width, height) * 0.24f
        paint.alpha = 230; canvas.drawCircle(width / 2f, height / 2f, radius + level * radius * 0.3f, paint)
        if (animation != null) {
            paint.alpha = ((1f - phase) * 150).toInt()
            canvas.drawCircle(width / 2f, height / 2f, radius * (1f + phase), paint)
        }
    }
    override fun onDetachedFromWindow() { listening(false); super.onDetachedFromWindow() }
}