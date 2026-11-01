package com.jarvis.assistant.lock

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.*
import com.jarvis.assistant.R

class GlassLockLayout(context: Context, private val action: (String) -> Unit) : FrameLayout(context) {
    val pulse = VoicePulse(context)
    val status = MonoUi.text(context, "Android security remains enabled.", 13f).apply { setTextColor(Color.WHITE); gravity = Gravity.CENTER }
    init {
        background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(Color.BLACK, Color.DKGRAY, Color.BLACK))
        val wallpaper = GlassWallpaper.load(context)
        if (wallpaper != null) addView(ImageView(context).apply {
            setImageBitmap(wallpaper); scaleType = ImageView.ScaleType.CENTER_CROP; alpha = 0.68f
        }, LayoutParams(-1, -1))
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
            setPadding(MonoUi.dp(context, 24), MonoUi.dp(context, 40), MonoUi.dp(context, 24), MonoUi.dp(context, 24))
        }
        column.addView(MonoUi.text(context, "J A R V I S", 13f).apply { setTextColor(Color.WHITE); gravity = Gravity.CENTER })
        column.addView(GlassClock(context, wallpaper), LinearLayout.LayoutParams(-1, -2).apply { topMargin = MonoUi.dp(context, 32) })
        column.addView(pulse, LinearLayout.LayoutParams(MonoUi.dp(context, 80), MonoUi.dp(context, 80)))
        column.addView(status, LinearLayout.LayoutParams(-1, -2))
        column.addView(MonoUi.button(context, "Speak my unlock phrase") { action("voice") })
        column.addView(MonoUi.button(context, "Use PIN / pattern / biometrics") { action("unlock") })
        val shortcuts = LinearLayout(context).apply { gravity = Gravity.CENTER; setPadding(0, MonoUi.dp(context, 20), 0, 0) }
        listOf("flashlight" to R.drawable.ic_flashlight, "camera" to android.R.drawable.ic_menu_camera).forEach { (label, icon) ->
            shortcuts.addView(ImageButton(context).apply {
                contentDescription = label; setImageResource(icon); imageTintList = android.content.res.ColorStateList.valueOf(Color.BLACK)
                background = MonoUi.surface(Color.WHITE, 100f); setPadding(16, 16, 16, 16); setOnClickListener { action(label) }
            }, LinearLayout.LayoutParams(MonoUi.dp(context, 56), MonoUi.dp(context, 56)).apply { setMargins(MonoUi.dp(context, 22), 0, MonoUi.dp(context, 22), 0) })
        }; column.addView(shortcuts)
        addView(ScrollView(context).apply { isFillViewport = true; addView(column) }, LayoutParams(-1, -1))
    }
}