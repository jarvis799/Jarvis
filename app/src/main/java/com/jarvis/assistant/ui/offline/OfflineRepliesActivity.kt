package com.jarvis.assistant.ui.offline

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.materialswitch.MaterialSwitch
import com.jarvis.assistant.JarvisApp
import com.jarvis.assistant.model.OfflineReply
import com.jarvis.assistant.util.ThemeManager

/** DEV STYLE: offline replies - answers that work with no internet at all. */
class OfflineRepliesActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyTheme(this)
        super.onCreate(savedInstanceState)
        val preferences = JarvisApp.instance.preferences
        val container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 24, 24, 24) }

        container.addView(TextView(this).apply { text = "Offline Replies"; textSize = 20f })
        container.addView(TextView(this).apply {
            text = OfflineReply.engineLabel(this@OfflineRepliesActivity)
            textSize = 12f
            setPadding(0, 8, 0, 16)
        })

        container.addView(TextView(this).apply { text = "Answer without internet"; textSize = 14f })
        container.addView(MaterialSwitch(this).apply {
            isChecked = preferences.offlineReplies
            setOnCheckedChangeListener { _, checked -> preferences.offlineReplies = checked }
        })

        container.addView(TextView(this).apply { text = "Try it"; textSize = 14f; setPadding(0, 20, 0, 6) })
        val question = EditText(this).apply { hint = "e.g. what time is it, battery, 12 times 8" }
        container.addView(question)
        val answer = TextView(this).apply { textSize = 14f; setPadding(0, 16, 0, 0) }
        container.addView(answer)
        container.addView(Button(this).apply {
            text = "Ask offline"
            setOnClickListener {
                val text = question.text.toString().trim()
                if (text.isEmpty()) return@setOnClickListener
                answer.text = "Thinking locally..."
                Thread {
                    val reply = OfflineReply.answer(this@OfflineRepliesActivity, text)
                    runOnUiThread { if (!isFinishing && !isDestroyed) answer.text = reply }
                }.start()
            }
        })

        container.addView(Button(this).apply { text = "Close"; setOnClickListener { finish() } })
        setContentView(ScrollView(this).apply { addView(container) })
    }
}
