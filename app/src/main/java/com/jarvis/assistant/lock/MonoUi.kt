package com.jarvis.assistant.lock

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.widget.*

object MonoUi {
    fun dp(c: Context, n: Int) = (n * c.resources.displayMetrics.density).toInt()
    fun surface(color: Int = Color.WHITE, radius: Float = 28f) = GradientDrawable().apply {
        setColor(color); cornerRadius = radius; setStroke(1, Color.LTGRAY)
    }
    fun text(c: Context, value: String, size: Float = 15f) = TextView(c).apply {
        text = value; textSize = size; setTextColor(Color.BLACK)
        setPadding(0, dp(c, 8), 0, dp(c, 8))
    }
    fun button(c: Context, value: String, action: () -> Unit) = Button(c).apply {
        text = value; isAllCaps = false; setTextColor(Color.WHITE); backgroundTintList = ColorStateList.valueOf(Color.BLACK)
        minHeight = dp(c, 48); setOnClickListener { action() }
    }
    fun toggle(c: Context, label: String, on: Boolean, action: (Boolean) -> Unit) = Switch(c).apply {
        text = label; setTextColor(Color.BLACK); isChecked = on; minHeight = dp(c, 56)
        thumbTintList = ColorStateList.valueOf(Color.WHITE)
        trackTintList = ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()), intArrayOf(Color.BLACK, Color.GRAY))
        setOnCheckedChangeListener { _, checked -> action(checked) }
    }
    fun page(a: Activity, title: String): LinearLayout {
        a.window.statusBarColor = Color.WHITE; a.window.navigationBarColor = Color.WHITE
        a.window.decorView.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        val col = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(a, 24), dp(a, 20), dp(a, 24), dp(a, 32)); setBackgroundColor(Color.WHITE) }
        col.addView(button(a, "Back") { a.finish() }); col.addView(text(a, title, 28f))
        a.setContentView(ScrollView(a).apply { setBackgroundColor(Color.WHITE); addView(col) })
        return col
    }
}