package com.jarvis.assistant.lock

import android.content.Context
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.widget.*

class GlassClock(context: Context, wallpaper: Bitmap?) : FrameLayout(context) {
    init {
        val radius = MonoUi.dp(context, 36).toFloat()
        background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
            intArrayOf(0x55FFFFFF, 0x18FFFFFF, 0x44000000)).apply {
            cornerRadius = radius; setStroke(MonoUi.dp(context, 1), 0x88FFFFFF.toInt())
        }
        clipToOutline = true; elevation = MonoUi.dp(context, 14).toFloat()
        if (wallpaper != null) addView(ImageView(context).apply {
            setImageBitmap(wallpaper); scaleType = ImageView.ScaleType.CENTER_CROP; alpha = 0.22f
            if (Build.VERSION.SDK_INT >= 31) setRenderEffect(RenderEffect.createBlurEffect(22f, 22f, Shader.TileMode.CLAMP))
        }, LayoutParams(-1, -1))
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setPadding(MonoUi.dp(context, 12), MonoUi.dp(context, 30), MonoUi.dp(context, 12), MonoUi.dp(context, 30))
        }
        content.addView(TextClock(context).apply {
            format12Hour = "h:mm"; format24Hour = "HH:mm"; setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-thin", Typeface.NORMAL); gravity = Gravity.CENTER
            setShadowLayer(10f, 0f, 2f, 0x55000000); textSize = 100f
            if (Build.VERSION.SDK_INT >= 26) setAutoSizeTextTypeUniformWithConfiguration(36, 104, 2, android.util.TypedValue.COMPLEX_UNIT_SP)
        }, LinearLayout.LayoutParams(-1, MonoUi.dp(context, 138)))
        content.addView(TextClock(context).apply {
            format12Hour = "EEEE, MMMM d"; format24Hour = "EEEE, MMMM d"
            setTextColor(Color.WHITE); textSize = 17f; gravity = Gravity.CENTER; letterSpacing = 0.04f
        }, LinearLayout.LayoutParams(-1, -2))
        addView(content, LayoutParams(-1, -2, Gravity.CENTER))
        minimumHeight = MonoUi.dp(context, 250)
    }
}