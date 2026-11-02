package com.jarvis.assistant.lock

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import kotlin.math.roundToInt

/** Monochrome premium surface system: pure black canvas, hairline glass panels, white type. */
object MonoUi {
    const val WHITE = 0xFFFFFFFF.toInt()
    const val DIM = 0xFF9A9A9A.toInt()
    const val FAINT = 0xFF6E6E6E.toInt()
    const val HAIRLINE = 0x1FFFFFFF
    const val PANEL = 0xFF101010.toInt()

    fun dp(c: Context, n: Int): Int = (n * c.resources.displayMetrics.density).roundToInt()

    fun surface(color: Int = WHITE, radius: Float = 28f): GradientDrawable = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius
        setStroke(1, 0x33000000)
    }

    fun panel(c: Context, radius: Int = 24, fill: Int = PANEL): GradientDrawable = GradientDrawable().apply {
        setColor(fill)
        cornerRadius = dp(c, radius).toFloat()
        setStroke(1, HAIRLINE)
    }

    fun pressable(drawable: GradientDrawable): RippleDrawable =
        RippleDrawable(ColorStateList.valueOf(0x26FFFFFF), drawable, null)

    fun heading(c: Context, value: String, size: Float = 30f): TextView = TextView(c).apply {
        text = value
        textSize = size
        setTextColor(WHITE)
        typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        letterSpacing = -0.01f
    }

    fun label(c: Context, value: String): TextView = TextView(c).apply {
        text = value.uppercase()
        textSize = 11f
        letterSpacing = 0.16f
        setTextColor(DIM)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setPadding(dp(c, 4), dp(c, 22), 0, dp(c, 10))
    }

    fun text(c: Context, value: String, size: Float = 14f): TextView = TextView(c).apply {
        text = value
        textSize = size
        setTextColor(WHITE)
        setLineSpacing(dp(c, 4).toFloat(), 1.1f)
        setPadding(0, dp(c, 6), 0, dp(c, 6))
    }

    fun button(c: Context, value: String, action: () -> Unit): TextView = TextView(c).apply {
        text = value
        textSize = 15f
        gravity = Gravity.CENTER
        setTextColor(Color.BLACK)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setPadding(dp(c, 18), dp(c, 16), dp(c, 18), dp(c, 16))
        background = pressable(GradientDrawable().apply { setColor(WHITE); cornerRadius = dp(c, 26).toFloat() })
        isClickable = true
        setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(c, 10) }
    }

    fun toggle(c: Context, label: String, on: Boolean, action: (Boolean) -> Unit): Switch = Switch(c).apply {
        text = label
        setTextColor(WHITE)
        textSize = 15f
        isChecked = on
        minHeight = dp(c, 52)
        setPadding(0, dp(c, 6), 0, dp(c, 6))
        thumbTintList = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
            intArrayOf(Color.BLACK, WHITE)
        )
        trackTintList = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
            intArrayOf(WHITE, 0xFF3A3A3A.toInt())
        )
        setOnCheckedChangeListener { _, checked -> action(checked) }
    }

    fun actionRow(c: Context, title: String, detail: String?, action: () -> Unit): LinearLayout {
        val row = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = pressable(panel(c))
            setPadding(dp(c, 18), dp(c, 15), dp(c, 16), dp(c, 15))
            isClickable = true
            setOnClickListener { action() }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(c, 10) }
        }
        val copy = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        copy.addView(TextView(c).apply {
            text = title
            setTextColor(WHITE)
            textSize = 15.5f
        })
        if (!detail.isNullOrBlank()) copy.addView(TextView(c).apply {
            text = detail
            setTextColor(DIM)
            textSize = 12.5f
            setPadding(0, dp(c, 4), 0, 0)
        })
        row.addView(copy, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(TextView(c).apply {
            text = "\u203A"
            setTextColor(DIM)
            textSize = 22f
            gravity = Gravity.CENTER
        })
        return row
    }

    fun footer(c: Context, value: String): TextView = TextView(c).apply {
        text = value
        textSize = 12f
        setTextColor(FAINT)
        setLineSpacing(dp(c, 4).toFloat(), 1.15f)
        setPadding(0, dp(c, 22), 0, 0)
    }

    fun page(a: Activity, title: String, subtitle: String? = null): LinearLayout {
        a.window.statusBarColor = Color.BLACK
        a.window.navigationBarColor = Color.BLACK
        a.window.decorView.systemUiVisibility = 0
        val content = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(a, 22), dp(a, 18), dp(a, 22), dp(a, 48))
            setBackgroundColor(Color.BLACK)
        }
        content.addView(TextView(a).apply {
            text = "\u2039   Back"
            setTextColor(DIM)
            textSize = 15f
            setPadding(0, dp(a, 12), dp(a, 16), dp(a, 12))
            isClickable = true
            setOnClickListener { a.finish() }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(a, 16) }
        })
        content.addView(heading(a, title))
        if (subtitle != null) content.addView(text(a, subtitle).apply {
            setTextColor(DIM)
            setPadding(0, dp(a, 10), 0, dp(a, 4))
        })
        a.setContentView(ScrollView(a).apply {
            setBackgroundColor(Color.BLACK)
            isFillViewport = true
            addView(content)
        })
        return content
    }
}