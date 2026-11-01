package com.jarvis.assistant.util

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.util.TypedValue
import android.view.View
import android.widget.Button
import android.widget.TextView
import com.jarvis.assistant.R

/**
 * DEV STYLE: the one design system for every screen.
 *
 * White neomorphic surfaces + glass plates: a soft top-left light, a quiet
 * bottom-right shade, a hairline lime rim and a specular sheen. Everything is
 * read from the active theme attributes, so a theme switch re-skins it.
 * Use these helpers instead of building drawables by hand per screen.
 */
object NeoGlass {

    // ---- tokens -------------------------------------------------------------

    fun color(context: Context, attr: Int, fallback: Int): Int {
        val value = TypedValue()
        val found = context.theme.resolveAttribute(attr, value, true)
        return if (found && value.type in TypedValue.TYPE_FIRST_COLOR_INT..TypedValue.TYPE_LAST_COLOR_INT) {
            value.data
        } else {
            fallback
        }
    }

    fun surfaceColor(context: Context): Int = color(context, R.attr.themeSurface, Color.WHITE)
    fun cardStart(context: Context): Int = color(context, R.attr.themeCardGlassStart, Color.WHITE)
    fun cardEnd(context: Context): Int = color(context, R.attr.themeCardGlassEnd, 0xFFF3F6EF.toInt())
    fun lineColor(context: Context): Int = color(context, R.attr.themeCardBorder, 0x29000000)
    fun textPrimary(context: Context): Int = color(context, R.attr.themeTextPrimary, 0xFF16210F.toInt())
    fun textSecondary(context: Context): Int = color(context, R.attr.themeTextSecondary, 0xFF52604A.toInt())
    fun textMuted(context: Context): Int = color(context, R.attr.themeTextMuted, 0xFF7C8A72.toInt())
    fun accent(context: Context): Int = color(context, R.attr.themeAccent, 0xFF65A30D.toInt())
    fun accentBright(context: Context): Int = color(context, R.attr.themeAccent2, 0xFFA3E635.toInt())
    fun accentDark(context: Context): Int = color(context, R.attr.themeAccent3, 0xFF3F6212.toInt())

    private fun withAlpha(color: Int, alpha: Float): Int =
        Color.argb((alpha.coerceIn(0f, 1f) * 255).toInt(), Color.red(color), Color.green(color), Color.blue(color))

    fun dp(context: Context, value: Float): Float = value * context.resources.displayMetrics.density

    // ---- surfaces -----------------------------------------------------------

    /** Neo card / plate: the default container for content on every screen. */
    fun surface(view: View, radiusDp: Float = 24f, withPadding: Boolean = true) {
        val context = view.context
        val radius = dp(context, radiusDp)
        val body = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(cardStart(context), cardEnd(context))).apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setStroke(dp(context, 1f).toInt().coerceAtLeast(1), lineColor(context))
        }
        val sheen = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(withAlpha(Color.WHITE, 0.55f), withAlpha(Color.WHITE, 0f))
        ).apply { cornerRadius = radius }
        val shade = GradientDrawable(
            GradientDrawable.Orientation.BR_TL,
            intArrayOf(withAlpha(accentDark(context), 0.10f), withAlpha(accentDark(context), 0f))
        ).apply { cornerRadius = radius }
        val rim = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius - dp(context, 1f)
            setStroke(dp(context, 0.8f).toInt().coerceAtLeast(1), withAlpha(Color.WHITE, 0.60f))
        }
        val inset = dp(context, 1f).toInt()
        val rimLayer = LayerDrawable(arrayOf(rim))
        rimLayer.setLayerInset(0, inset, inset, inset, inset)

        view.background = LayerDrawable(arrayOf(body, sheen, shade, rimLayer))
        view.elevation = dp(context, 6f)
        view.clipToOutline = false
        if (withPadding) {
            val p = dp(context, 18f).toInt()
            view.setPadding(p, dp(context, 16f).toInt(), p, dp(context, 16f).toInt())
        }
    }

    /** Inset well: inputs, list rows, anything that sits *inside* a card. */
    fun well(view: View, radiusDp: Float = 18f) {
        val context = view.context
        val radius = dp(context, radiusDp)
        val body = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(
            withAlpha(accentDark(context), 0.05f),
            withAlpha(Color.WHITE, 0.92f)
        )).apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setStroke(dp(context, 1f).toInt().coerceAtLeast(1), withAlpha(lineColor(context), 0.85f))
        }
        view.background = body
        view.elevation = 0f
    }

    /** Floating glass dock / pill bar. */
    fun dock(view: View, radiusDp: Float = 34f) {
        val context = view.context
        val radius = dp(context, radiusDp)
        val body = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(
            color(context, R.attr.themeDockGlassStart, Color.WHITE),
            color(context, R.attr.themeDockGlassEnd, 0xFFF2F6ED.toInt())
        )).apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setStroke(dp(context, 1f).toInt().coerceAtLeast(1), color(context, R.attr.themeDockBorder, 0x33000000))
        }
        val gloss = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(withAlpha(Color.WHITE, 0.85f), withAlpha(Color.WHITE, 0f))
        ).apply { cornerRadius = radius }
        view.background = LayerDrawable(arrayOf(body, gloss))
        view.elevation = dp(context, 10f)
    }

    // ---- controls -----------------------------------------------------------

    /** Primary action: lime glass pill, white bold caps. */
    fun primaryButton(button: Button) {
        val context = button.context
        val radius = dp(context, 28f)
        val body = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(
            accent(context), accentBright(context)
        )).apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setStroke(dp(context, 1f).toInt().coerceAtLeast(1), withAlpha(Color.WHITE, 0.35f))
        }
        val gloss = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(withAlpha(Color.WHITE, 0.30f), withAlpha(Color.WHITE, 0f))
        ).apply { cornerRadius = radius }
        button.background = LayerDrawable(arrayOf(body, gloss))
        button.elevation = dp(context, 7f)
        button.setTextColor(Color.WHITE)
        button.isAllCaps = true
        button.letterSpacing = 0.08f
        button.typeface = Typeface.DEFAULT_BOLD
        button.textSize = 13f
        button.setPadding(dp(context, 22f).toInt(), dp(context, 13f).toInt(), dp(context, 22f).toInt(), dp(context, 13f).toInt())
        button.minimumHeight = dp(context, 46f).toInt()
        button.isSoundEffectsEnabled = false
    }

    /** Secondary action: white neo plate with a lime hairline. */
    fun secondaryButton(button: Button) {
        val context = button.context
        val radius = dp(context, 28f)
        val body = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(
            withAlpha(Color.WHITE, 0.97f), color(context, R.attr.themeAccentLight, 0xFFEAF7D9.toInt())
        )).apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setStroke(dp(context, 1.5f).toInt().coerceAtLeast(1), withAlpha(accent(context), 0.55f))
        }
        button.background = body
        button.elevation = dp(context, 3f)
        button.setTextColor(accentDark(context))
        button.isAllCaps = true
        button.letterSpacing = 0.08f
        button.typeface = Typeface.DEFAULT_BOLD
        button.textSize = 13f
        button.setPadding(dp(context, 22f).toInt(), dp(context, 13f).toInt(), dp(context, 22f).toInt(), dp(context, 13f).toInt())
        button.minimumHeight = dp(context, 46f).toInt()
        button.isSoundEffectsEnabled = false
    }

    /** Small capsule chip. */
    fun chip(view: View) {
        val context = view.context
        val radius = dp(context, 40f)
        val body = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(color(context, R.attr.themeChipBg, 0xF0F6FAEF.toInt()))
            setStroke(dp(context, 1f).toInt().coerceAtLeast(1), withAlpha(accent(context), 0.28f))
        }
        view.background = body
        view.elevation = dp(context, 2f)
    }

    /** Uppercase, tracked, accent section label. */
    fun sectionLabel(textView: TextView) {
        val context = textView.context
        textView.setTextColor(accentDark(context))
        textView.typeface = Typeface.DEFAULT_BOLD
        textView.isAllCaps = true
        textView.letterSpacing = 0.12f
        textView.textSize = 11f
    }

    /** Body copy defaults so new screens never drift in colour or weight. */
    fun bodyText(textView: TextView) {
        val context = textView.context
        textView.setTextColor(textSecondary(context))
        textView.textSize = 14f
        textView.typeface = Typeface.DEFAULT
        textView.letterSpacing = 0.01f
    }
}