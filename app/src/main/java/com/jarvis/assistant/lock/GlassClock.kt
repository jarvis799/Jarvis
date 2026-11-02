package com.jarvis.assistant.lock

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextClock

/** Oversized thin clock with the date underneath, exactly as Android's own clock ticker. */
class GlassClock(context: Context) : LinearLayout(context) {
    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        clipChildren = false
        addView(TextClock(context).apply {
            format12Hour = "h:mm"
            format24Hour = "HH:mm"
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-thin", Typeface.NORMAL)
            gravity = Gravity.CENTER
            letterSpacing = -0.04f
            includeFontPadding = false
            setShadowLayer(22f, 0f, 8f, 0x73000000)
            setAutoSizeTextTypeUniformWithConfiguration(54, 108, 4, TypedValue.COMPLEX_UNIT_SP)
            minHeight = MonoUi.dp(context, 118)
        }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(TextClock(context).apply {
            format12Hour = "EEEE, d MMMM"
            format24Hour = "EEEE, d MMMM"
            setTextColor(0xF2FFFFFF.toInt())
            textSize = 16f
            typeface = Typeface.create("sans-serif-normal", Typeface.NORMAL)
            gravity = Gravity.CENTER
            letterSpacing = 0.03f
        }, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            topMargin = MonoUi.dp(context, 2)
        })
    }
}