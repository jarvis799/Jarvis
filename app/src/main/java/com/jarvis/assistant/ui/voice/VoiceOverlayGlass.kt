package com.jarvis.assistant.ui.voice

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable

/**
 * DEV STYLE: palette and translucent glass surfaces shared by the Jarvis voice overlay
 * and the animated edge-lighting layer. Cyan, electric blue, violet and magenta light
 * over a near-black void, with a thin luminous border on every panel.
 */
internal object VoiceOverlayGlass {

    const val CYAN = 0xFF22E3FF.toInt()
    const val BLUE = 0xFF2E7BFF.toInt()
    const val VIOLET = 0xFFA855F7.toInt()
    const val MAGENTA = 0xFFFF3DDC.toInt()

    /** Four-colour spectrum shared by the flowing accent strips. */
    val FLOW = intArrayOf(CYAN, BLUE, VIOLET, MAGENTA)

    const val SCRIM = 0xB3050810.toInt()
    const val CARD_FILL_TOP = 0x6B2B435E.toInt()
    const val CARD_FILL_BOTTOM = 0x3D12203A.toInt()
    const val PANEL_FILL_TOP = 0x73162438.toInt()
    const val PANEL_FILL_BOTTOM = 0x59101A2E.toInt()
    const val CARD_STROKE = 0x80E6F1FF.toInt()
    const val PANEL_STROKE = 0x5CE6F1FF.toInt()
    const val RIM_STROKE = 0x3DFFFFFF
    const val SEAT_FILL = 0x2EFFFFFF.toInt()

    const val TEXT_PRIMARY = 0xFFECF3FF.toInt()
    const val TEXT_SECONDARY = 0xB3CFE0F5.toInt()
    const val TEXT_MUTED = 0x80AFC4DC.toInt()

    private const val SHEEN_TOP = 0x2BFFFFFF
    private const val SHEEN_SOFT = 0x1FFFFFFF
    private const val SHEEN_END = 0x00FFFFFF

    fun glassCard(radiusPx: Float): LayerDrawable =
        glass(CARD_FILL_TOP, CARD_FILL_BOTTOM, CARD_STROKE, radiusPx)

    fun glassPanel(radiusPx: Float): LayerDrawable =
        glass(PANEL_FILL_TOP, PANEL_FILL_BOTTOM, PANEL_STROKE, radiusPx)

    private fun glass(fillTop: Int, fillBottom: Int, stroke: Int, radiusPx: Float): LayerDrawable {
        val body = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(fillTop, fillBottom)
        ).apply {
            cornerRadius = radiusPx
            setStroke(2, stroke)
        }
        val sheen = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(SHEEN_TOP, SHEEN_END)
        ).apply {
            cornerRadius = radiusPx
        }
        val rim = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(RIM_STROKE, 0x00FFFFFF)
        ).apply {
            cornerRadius = radiusPx - 2f
            setStroke(2, RIM_STROKE)
        }
        return LayerDrawable(arrayOf(body, sheen, rim))
    }

    /** Frosted shell for a text field: soft glass body with a crisp luminous rim. */
    fun fieldShell(radiusPx: Float): LayerDrawable {
        val body = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(0x3D172336, 0x24101A2A)
        ).apply {
            cornerRadius = radiusPx
            setStroke(2, 0x3DFFFFFF)
        }
        val sheen = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(SHEEN_SOFT, SHEEN_END)
        ).apply {
            cornerRadius = radiusPx
        }
        return LayerDrawable(arrayOf(body, sheen))
    }

    /** Thin strip that flows through cyan, blue, violet and magenta. */
    fun accentStrip(radiusPx: Float): GradientDrawable =
        GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, FLOW).apply {
            cornerRadius = radiusPx
        }

    fun radialGlow(color: Int, alpha: Int, radiusPx: Float): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            gradientType = GradientDrawable.RADIAL_GRADIENT
            gradientRadius = radiusPx
            colors = intArrayOf(
                Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color)),
                Color.TRANSPARENT
            )
        }

    fun pill(fill: Int, stroke: Int, radiusPx: Float): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radiusPx
            setColor(fill)
            setStroke(2, stroke)
        }

    fun dot(color: Int): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
        }
}
