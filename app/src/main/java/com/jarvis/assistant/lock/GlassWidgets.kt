package com.jarvis.assistant.lock

import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import com.jarvis.assistant.util.WeatherData

/**
 * Frosted-glass weather card. It stays hidden until Android's real location + Open-Meteo
 * reading arrives, so the lock screen never shows a guessed temperature.
 */
class WeatherPanel(context: Context) : LinearLayout(context) {
    private val city = TextView(context)
    private val condition = TextView(context)
    private val temperature = TextView(context)

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = MonoUi.panel(context, 26, 0x8C0E0E0E.toInt())
        setPadding(MonoUi.dp(context, 20), MonoUi.dp(context, 17), MonoUi.dp(context, 20), MonoUi.dp(context, 17))
        visibility = GONE

        val left = LinearLayout(context).apply { orientation = VERTICAL }
        city.apply {
            textSize = 14f
            setTextColor(MonoUi.WHITE)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        condition.apply {
            textSize = 10.5f
            letterSpacing = 0.14f
            setTextColor(0xB3FFFFFF.toInt())
            setPadding(0, MonoUi.dp(context, 5), 0, 0)
        }
        left.addView(city)
        left.addView(condition)
        temperature.apply {
            textSize = 38f
            setTextColor(MonoUi.WHITE)
            typeface = Typeface.create("sans-serif-thin", Typeface.NORMAL)
            gravity = Gravity.END
        }
        addView(left, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        addView(temperature, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
    }

    fun bind(data: WeatherData) {
        city.text = data.cityName
        condition.text = data.condition.uppercase()
        temperature.text = data.temperature
        visibility = VISIBLE
    }
}