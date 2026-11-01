package com.jarvis.assistant.model

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

object OfflineModelStyle {
    val background = Color.rgb(10, 18, 15)
    val surface = Color.rgb(22, 34, 28)
    val lime = Color.rgb(204, 255, 143)
    val mint = Color.rgb(174, 221, 197)
    fun dp(context: Context, value: Int) = (value * context.resources.displayMetrics.density).toInt()
    fun column(context: Context) = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    fun text(context: Context, value: String, size: Float = 14f, primary: Boolean = false) = TextView(context).apply {
        text = value; textSize = size; setTextColor(if (primary) Color.WHITE else mint)
        if (primary) setTypeface(typeface, Typeface.BOLD)
        setPadding(0, dp(context, 6), 0, dp(context, 6))
    }
    fun panel(context: Context, view: LinearLayout) {
        view.setPadding(dp(context, 18), dp(context, 16), dp(context, 18), dp(context, 16))
        view.background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
            intArrayOf(surface, background)).apply {
            cornerRadius = dp(context, 24).toFloat(); setStroke(dp(context, 1), Color.argb(65, 204, 255, 200))
        }
        view.layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(context, 14) }
    }
    fun button(context: Context, label: String, action: () -> Unit) = Button(context).apply {
        text = label; isAllCaps = false; setTextColor(OfflineModelStyle.background)
        minHeight = dp(context, 48)
        background = GradientDrawable().apply { setColor(lime); cornerRadius = dp(context, 18).toFloat() }
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(context, 10) }
        setOnClickListener { action() }
    }
}