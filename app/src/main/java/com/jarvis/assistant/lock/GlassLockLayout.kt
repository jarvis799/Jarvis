package com.jarvis.assistant.lock

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.jarvis.assistant.R
import com.jarvis.assistant.util.WeatherData

/** Premium monochrome lock surface: wallpaper, oversized clock, glass widgets, real shortcuts. */
class GlassLockLayout(context: Context, private val action: (String) -> Unit) : FrameLayout(context) {

    val pulse = VoicePulse(context)
    val status = TextView(context).apply {
        textSize = 12.5f
        setTextColor(0xCCFFFFFF.toInt())
        gravity = Gravity.CENTER
        setLineSpacing(MonoUi.dp(context, 3).toFloat(), 1.05f)
        setPadding(MonoUi.dp(context, 8), MonoUi.dp(context, 14), MonoUi.dp(context, 8), 0)
    }
    private val battery = TextView(context).apply {
        textSize = 11.5f
        letterSpacing = 0.1f
        setTextColor(0xD9FFFFFF.toInt())
        gravity = Gravity.END
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val weatherPanel = WeatherPanel(context)

    init {
        setBackgroundColor(Color.BLACK)

        val wallpaper = GlassWallpaper.load(context)
        if (wallpaper != null) addView(ImageView(context).apply {
            setImageBitmap(wallpaper)
            scaleType = ImageView.ScaleType.CENTER_CROP
        }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        addView(View(context).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0x66000000, 0x14000000, 0x00000000, 0x59000000, 0xF2000000.toInt())
            )
        }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(MonoUi.dp(context, 20), MonoUi.dp(context, 52), MonoUi.dp(context, 20), MonoUi.dp(context, 26))
        }

        val top = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        top.addView(TextView(context).apply {
            text = "J A R V I S"
            textSize = 11f
            letterSpacing = 0.28f
            setTextColor(0xF2FFFFFF.toInt())
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        top.addView(battery, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        column.addView(top, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        column.addView(GlassClock(context), LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = MonoUi.dp(context, 10) })

        column.addView(weatherPanel, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = MonoUi.dp(context, 20) })

        column.addView(View(context), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        val auth = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        auth.addView(pulse, LinearLayout.LayoutParams(MonoUi.dp(context, 62), MonoUi.dp(context, 62)))
        auth.addView(status, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        val buttons = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        buttons.addView(pill(context, "Unlock", filled = true) { action("unlock") },
            LinearLayout.LayoutParams(0, MonoUi.dp(context, 50), 1.3f))
        buttons.addView(pill(context, "Voice", filled = false) { action("voice") },
            LinearLayout.LayoutParams(0, MonoUi.dp(context, 50), 1f).apply {
                marginStart = MonoUi.dp(context, 10)
            })
        auth.addView(buttons, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = MonoUi.dp(context, 18) })

        auth.addView(TextView(context).apply {
            text = "Forgot voice phrase?"
            textSize = 12f
            setTextColor(0xB3FFFFFF.toInt())
            gravity = Gravity.CENTER
            setPadding(0, MonoUi.dp(context, 14), 0, 0)
            isClickable = true
            setOnClickListener { action("recovery") }
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        column.addView(auth, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        val shortcuts = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        shortcuts.addView(circle(context, R.drawable.ic_flashlight, "Flashlight") { action("flashlight") })
        shortcuts.addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))
        shortcuts.addView(circle(context, android.R.drawable.ic_menu_camera, "Camera") { action("camera") })
        column.addView(shortcuts, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = MonoUi.dp(context, 26) })

        column.addView(View(context).apply {
            background = GradientDrawable().apply {
                setColor(0x66FFFFFF)
                cornerRadius = MonoUi.dp(context, 3).toFloat()
            }
        }, LinearLayout.LayoutParams(MonoUi.dp(context, 118), MonoUi.dp(context, 4)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            topMargin = MonoUi.dp(context, 20)
        })

        addView(column, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    fun setBattery(percent: Int, charging: Boolean) {
        battery.text = (if (charging) "\u26A1 " else "") + "$percent%"
    }

    fun setWeather(data: WeatherData) = weatherPanel.bind(data)

    private fun pill(context: Context, label: String, filled: Boolean, run: () -> Unit): TextView =
        TextView(context).apply {
            text = label
            textSize = 15f
            gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(if (filled) Color.BLACK else Color.WHITE)
            background = MonoUi.pressable(GradientDrawable().apply {
                cornerRadius = MonoUi.dp(context, 25).toFloat()
                if (filled) {
                    setColor(Color.WHITE)
                } else {
                    setColor(0x26FFFFFF)
                    setStroke(MonoUi.dp(context, 1), 0x66FFFFFF)
                }
            })
            isClickable = true
            setOnClickListener { run() }
        }

    private fun circle(context: Context, icon: Int, description: String, run: () -> Unit): FrameLayout {
        val size = MonoUi.dp(context, 56)
        return FrameLayout(context).apply {
            background = MonoUi.pressable(GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0x33FFFFFF)
                setStroke(MonoUi.dp(context, 1), 0x59FFFFFF)
            })
            contentDescription = description
            isClickable = true
            setOnClickListener { run() }
            addView(ImageView(context).apply {
                setImageResource(icon)
                imageTintList = ColorStateList.valueOf(Color.WHITE)
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                setPadding(MonoUi.dp(context, 14), MonoUi.dp(context, 14), MonoUi.dp(context, 14), MonoUi.dp(context, 14))
            }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
            layoutParams = LinearLayout.LayoutParams(size, size)
        }
    }
}