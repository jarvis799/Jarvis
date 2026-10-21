package com.jarvis.assistant.util

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.widget.Button

/**
 * DEV STYLE: shared premium styling for the programmatic screens
 * (Photo Memory, Browser Agent, Share to Jarvis, Control Center).
 * Deep violet gradient pills with white text for primary actions,
 * soft lavender pills for secondary actions.
 */
object PremiumUi {

    // Pastel rainbow palette (same colors as the edge lighting).
    val PASTEL = intArrayOf(
        0xFFFF9DAD.toInt(), 0xFFFFD0C2.toInt(), 0xFFFFF3A6.toInt(),
        0xFFA8F2D0.toInt(), 0xFF91E8F5.toInt(), 0xFF9BCBFF.toInt(),
        0xFFD5A6FF.toInt()
    )

    private const val VIOLET_DARK = 0xFF5B4B8A
    private const val VIOLET = 0xFF8B5CF6
    private const val LAVENDER_SOFT = 0xFFF3F0FF
    private const val TEXT_VIOLET = 0xFF4C3D75

    /** Primary action: deep gradient pill, white bold text. */
    fun premiumButton(b: Button) {
        val body = GradientDrawable()
        body.shape = GradientDrawable.RECTANGLE
        body.cornerRadius = 28f
        body.colors = intArrayOf(VIOLET_DARK.toInt(), VIOLET.toInt())
        body.orientation = GradientDrawable.Orientation.LEFT_RIGHT
        body.setStroke(dp(1), 0x59FFFFFF)
        val sheen = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(0x33FFFFFF, 0x00FFFFFF)
        )
        sheen.cornerRadius = 28f
        b.background = LayerDrawable(arrayOf(body, sheen))
        b.elevation = dp(6).toFloat()
        b.setTextColor(Color.WHITE)
        b.isAllCaps = true
        b.letterSpacing = 0.08f
        b.typeface = Typeface.DEFAULT_BOLD
        b.textSize = 13f
        b.setPadding(dp(22), dp(13), dp(22), dp(13))
        b.minimumHeight = dp(44)
        b.isSoundEffectsEnabled = false
    }

    /** Secondary action: soft lavender pill with violet text. */
    fun secondaryButton(b: Button) {
        val gd = GradientDrawable()
        gd.shape = GradientDrawable.RECTANGLE
        gd.cornerRadius = 28f
        gd.setColor(LAVENDER_SOFT.toInt())
        gd.setStroke(dp(2), VIOLET.toInt())
        b.background = gd
        b.setTextColor(TEXT_VIOLET.toInt())
        b.isAllCaps = true
        b.letterSpacing = 0.08f
        b.typeface = Typeface.DEFAULT_BOLD
        b.textSize = 13f
        b.setPadding(dp(22), dp(13), dp(22), dp(13))
        b.minimumHeight = dp(44)
        b.isSoundEffectsEnabled = false
    }

    /** Premium glass card surface for any container view. */
    fun premiumCard(v: android.view.View) {
        val body = GradientDrawable()
        body.shape = GradientDrawable.RECTANGLE
        body.cornerRadius = 26f
        body.colors = intArrayOf(0xFFFDFCFF.toInt(), LAVENDER_SOFT.toInt())
        body.orientation = GradientDrawable.Orientation.TL_BR
        body.setStroke(dp(1), 0x335B4B8A)
        val rim = GradientDrawable()
        rim.shape = GradientDrawable.RECTANGLE
        rim.cornerRadius = 24f
        rim.setStroke(dp(1), 0x2E8B5CF6)
        val sheen = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(0x26FFFFFF, 0x00FFFFFF)
        )
        sheen.cornerRadius = 26f
        v.background = LayerDrawable(arrayOf(body, sheen, rim))
        v.elevation = dp(8).toFloat()
        v.setPadding(dp(18), dp(16), dp(18), dp(16))
    }

    /** Small-caps violet section label. */
    fun sectionTitle(tv: android.widget.TextView) {
        tv.setTextColor(TEXT_VIOLET.toInt())
        tv.typeface = Typeface.DEFAULT_BOLD
        tv.isAllCaps = true
        tv.letterSpacing = 0.12f
        tv.textSize = 12f
    }

    private fun dp(v: Int): Int = (v * 1.5f).toInt()
}
