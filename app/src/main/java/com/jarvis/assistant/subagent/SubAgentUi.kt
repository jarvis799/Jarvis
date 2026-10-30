package com.jarvis.assistant.subagent

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import com.jarvis.assistant.R

/**
 * DEV STYLE — shared white + lime glass styling for every sub agent screen.
 * Colors come from theme attributes, so light / dark / HUD themes all stay
 * consistent without hardcoded values.
 */
class SubAgentUi(private val activity: Activity) {

    fun dp(value: Int): Int = (value * activity.resources.displayMetrics.density).toInt()

    fun color(attr: Int, fallback: Int = Color.GRAY): Int {
        val tv = TypedValue()
        return if (activity.theme.resolveAttribute(attr, tv, true) && tv.data != 0) tv.data else fallback
    }

    val accent: Int get() = color(R.attr.themeAccent, Color.parseColor("#22C55E"))
    val accentLight: Int get() = color(R.attr.themeAccentLight, Color.parseColor("#DCFCE7"))
    val textPrimary: Int get() = color(R.attr.themeTextPrimary, Color.parseColor("#1A1A1A"))
    val textSecondary: Int get() = color(R.attr.themeTextSecondary, Color.parseColor("#444444"))
    val textMuted: Int get() = color(R.attr.themeTextMuted, Color.parseColor("#777777"))
    val cardBg: Int get() = color(R.attr.themeCardBg, Color.parseColor("#CCFFFFFF"))
    val cardBorder: Int get() = color(R.attr.themeCardBorder, Color.parseColor("#3322C55E"))

    fun alpha(source: Int, factor: Float): Int =
        Color.argb((255 * factor).coerceIn(0f, 1f).toInt(), Color.red(source), Color.green(source), Color.blue(source))

    fun rounded(fill: Int, radius: Int = 20, stroke: Int? = null): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(radius).toFloat()
            setColor(fill)
            if (stroke != null) setStroke(dp(1), stroke)
        }

    fun card(): LinearLayout = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        background = rounded(cardBg, 22, cardBorder)
        setPadding(dp(16), dp(16), dp(16), dp(16))
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(12) }
    }

    fun title(text: String, size: Float = 15f): TextView = TextView(activity).apply {
        this.text = text
        setTextColor(textPrimary)
        textSize = size
        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        includeFontPadding = false
    }

    fun body(text: String, size: Float = 12.5f): TextView = TextView(activity).apply {
        this.text = text
        setTextColor(textSecondary)
        textSize = size
        setLineSpacing(0f, 1.18f)
    }

    fun hint(text: String, size: Float = 12f): TextView = TextView(activity).apply {
        this.text = text
        setTextColor(textMuted)
        textSize = size
        setLineSpacing(0f, 1.18f)
    }

    fun badge(text: String, toneColor: Int): TextView = TextView(activity).apply {
        this.text = text
        textSize = 10f
        setTextColor(toneColor)
        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        letterSpacing = 0.06f
        background = rounded(alpha(toneColor, 0.14f), 8, alpha(toneColor, 0.35f))
        setPadding(dp(8), dp(4), dp(8), dp(4))
        gravity = Gravity.CENTER
    }

    fun pillButton(text: String, filled: Boolean = true): TextView = TextView(activity).apply {
        this.text = text
        textSize = 13.5f
        gravity = Gravity.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        setPadding(dp(16), dp(12), dp(16), dp(12))
        isClickable = true
        isFocusable = true
        if (filled) {
            background = rounded(accent, 16)
            setTextColor(Color.parseColor("#0B1F10"))
        } else {
            background = rounded(alpha(cardBg, 0.6f), 16, alpha(accent, 0.45f))
            setTextColor(accent)
        }
    }

    fun iconBubble(color: Int, iconRes: Int): LinearLayout = LinearLayout(activity).apply {
        background = rounded(alpha(color, 0.14f), 14)
        gravity = Gravity.CENTER
        layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))
        addView(android.widget.ImageView(activity).apply {
            setImageResource(iconRes)
            imageTintList = android.content.res.ColorStateList.valueOf(color)
            layoutParams = LinearLayout.LayoutParams(dp(22), dp(22))
            contentDescription = null
        })
    }

    fun backButton(onClick: () -> Unit): LinearLayout = LinearLayout(activity).apply {
        background = rounded(alpha(cardBg, 0.8f), 14, alpha(cardBorder, 0.5f))
        gravity = Gravity.CENTER
        isClickable = true
        isFocusable = true
        contentDescription = "Back"
        layoutParams = LinearLayout.LayoutParams(dp(40), dp(40))
        addView(android.widget.ImageView(activity).apply {
            setImageResource(R.drawable.ic_back)
            imageTintList = android.content.res.ColorStateList.valueOf(textPrimary)
            layoutParams = LinearLayout.LayoutParams(dp(18), dp(18))
            contentDescription = null
        })
        setOnClickListener { onClick() }
    }

    fun divider(): android.view.View = android.view.View(activity).apply {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(1)
        ).apply { topMargin = dp(14); bottomMargin = dp(14) }
        setBackgroundColor(alpha(textMuted, 0.2f))
    }

    /** Header used by every sub agent screen: back, title, subtitle, optional action. */
    fun header(titleText: String, subtitleText: String, onBack: () -> Unit, action: android.view.View? = null): LinearLayout {
        return LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(backButton(onBack))
            val column = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    .apply { marginStart = dp(12); marginEnd = dp(8) }
                addView(title(titleText, 21f))
                addView(hint(subtitleText, 12f).apply { setPadding(0, dp(3), 0, 0) })
            }
            addView(column)
            if (action != null) addView(action)
        }
    }
}