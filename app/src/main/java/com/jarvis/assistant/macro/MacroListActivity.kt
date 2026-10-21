package com.jarvis.assistant.macro

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.jarvis.assistant.notifications.JarvisNotificationListenerService
import com.jarvis.assistant.util.ThemeManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * DEV STYLE - Macro Center.
 * The MacroDroid-style home of Jarvis automations: toggle, run now,
 * delete, and the recent activity of every macro.
 */
class MacroListActivity : AppCompatActivity() {

    private val fmt = SimpleDateFormat("dd MMM, hh:mm a", Locale.ENGLISH)

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyTheme(this)
        super.onCreate(savedInstanceState)
        title = "Macro Center"
        render()
    }

    private fun render() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#FFFFFF"))
            setPadding(dp(20), dp(24), dp(20), dp(32))
        }

        root.addView(TextView(this).apply {
            text = "Macro Center"
            textSize = 22f
            setTextColor(Color.parseColor("#111827"))
            typeface = Typeface.DEFAULT_BOLD
        })
        root.addView(TextView(this).apply {
            text = "Jarvis ke bolei automation banano jay - emon: Jarvis, WhatsApp e message asle deep-think kore reply diye de."
            textSize = 13f
            setTextColor(Color.parseColor("#6B7280"))
            setPadding(0, dp(6), 0, dp(4))
        })
        if (!JarvisNotificationListenerService.isEnabled()) {
            root.addView(TextView(this).apply {
                text = "Notification Access off ache - Settings > Notification access e Jarvis on korun, notification-trigger automation er jonno."
                textSize = 12f
                setTextColor(Color.parseColor("#DC2626"))
                setPadding(0, dp(4), 0, dp(4))
            })
        }

        val macros = MacroStore.list(this)
        if (macros.isEmpty()) {
            root.addView(TextView(this).apply {
                text = "Ekono kono automation nei.\nJarvis ke bolen - ekhane dekha jabe."
                textSize = 14f
                setTextColor(Color.parseColor("#9CA3AF"))
                setPadding(0, dp(28), 0, 0)
            })
        }
        macros.forEach { macro -> root.addView(macroCard(macro)) }

        val history = MacroStore.history(this)
        if (history.isNotEmpty()) {
            root.addView(TextView(this).apply {
                text = "Recent Activity"
                textSize = 15f
                setTextColor(Color.parseColor("#111827"))
                typeface = Typeface.DEFAULT_BOLD
                setPadding(0, dp(28), 0, dp(8))
            })
            history.take(10).forEach { e ->
                root.addView(TextView(this).apply {
                    val ok = e.status == "OK"
                    text = fmt.format(Date(e.at)) + " - " + e.macroName + (if (ok) "  OK" else "  " + e.status)
                    textSize = 12f
                    setTextColor(if (ok) Color.parseColor("#16A34A") else Color.parseColor("#DC2626"))
                    setPadding(0, dp(2), 0, dp(2))
                })
            }
        }

        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun macroCard(macro: Macro): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(Color.parseColor("#FFFFFF"))
                setStroke(1, Color.parseColor("#E5E7EB"))
            }
            setPadding(dp(14), dp(14), dp(14), dp(14))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 0, 0, dp(12))
            }
        }

        card.addView(TextView(this).apply {
            text = macro.name
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#111827"))
        })
        card.addView(TextView(this).apply {
            text = macro.trigger.describe() + "\n" + macro.actions.joinToString("\n") { "- " + it.describe() }
            textSize = 12f
            setTextColor(Color.parseColor("#6B7280"))
            setPadding(0, dp(4), 0, 0)
        })
        if (macro.lastFiredAt > 0) {
            card.addView(TextView(this).apply {
                val ok = macro.lastStatus == "OK"
                text = "Last run: " + fmt.format(Date(macro.lastFiredAt)) + (if (ok) "  OK" else "  " + macro.lastStatus)
                textSize = 11f
                setTextColor(if (ok) Color.parseColor("#16A34A") else Color.parseColor("#DC2626"))
                setPadding(0, dp(3), 0, 0)
            })
        }

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(10), 0, 0)
        }
        row.addView(Switch(this).apply {
            text = "On"
            isChecked = macro.enabled
            setOnCheckedChangeListener { _, checked ->
                MacroStore.save(this@MacroListActivity, macro.copy(enabled = checked))
            }
        })
        row.addView(Button(this).apply {
            text = "Run"
            setBackgroundColor(Color.parseColor("#84CC16"))
            setTextColor(Color.parseColor("#FFFFFF"))
            setOnClickListener {
                MacroEngine.get(this@MacroListActivity).runByName(macro.name)
                Toast.makeText(this@MacroListActivity, "Chaliye dilam - Recent Activity e result ashbe", Toast.LENGTH_SHORT).show()
            }
        })
        row.addView(Button(this).apply {
            text = "Delete"
            setBackgroundColor(Color.parseColor("#FEE2E2"))
            setTextColor(Color.parseColor("#B91C1C"))
            setOnClickListener {
                MacroStore.delete(this@MacroListActivity, macro.id)
                render()
            }
        })
        card.addView(row)
        return card
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
