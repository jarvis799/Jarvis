package com.jarvis.assistant.util

import android.widget.Button
import android.widget.TextView
import android.view.View

/**
 * DEV STYLE: premium styling for the programmatic screens
 * (Photo Memory, Browser Agent, Share to Jarvis, Control Center).
 *
 * These are thin adapters over [NeoGlass] so those screens share the exact same
 * white neo-glass system as the XML screens. Public API is unchanged.
 */
object PremiumUi {

    // Pastel rainbow palette (same colors as the edge lighting).
    val PASTEL = intArrayOf(
        0xFFFF9DAD.toInt(), 0xFFFFD0C2.toInt(), 0xFFFFF3A6.toInt(),
        0xFFA8F2D0.toInt(), 0xFF91E8F5.toInt(), 0xFF9BCBFF.toInt(),
        0xFFD5A6FF.toInt()
    )

    /** Primary action: lime glass pill, white bold text. */
    fun premiumButton(b: Button) = NeoGlass.primaryButton(b)

    /** Secondary action: white neo plate with a lime hairline. */
    fun secondaryButton(b: Button) = NeoGlass.secondaryButton(b)

    /** Premium glass card surface for any container view. */
    fun premiumCard(v: View) = NeoGlass.surface(v, radiusDp = 24f)

    /** Small-caps accent section label. */
    fun sectionTitle(tv: TextView) = NeoGlass.sectionLabel(tv)
}