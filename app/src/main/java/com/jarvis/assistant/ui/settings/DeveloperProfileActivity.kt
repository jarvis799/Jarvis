package com.jarvis.assistant.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.jarvis.assistant.R
import com.jarvis.assistant.security.OwnerProfile
import com.jarvis.assistant.util.ThemeManager

/**
 * The official developer profile screen. Every value comes from [OwnerProfile] so the
 * About screen and Jarvis's spoken creator answers can never disagree.
 */
class DeveloperProfileActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyTheme(this)
        super.onCreate(savedInstanceState)
        setContentView(buildRoot())
    }

    private fun buildRoot(): LinearLayout {
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 28, 28, 28)
            setBackgroundColor(ContextCompat.getColor(this@DeveloperProfileActivity, R.color.studio_surface))
        }

        column.addView(TextView(this).apply { text = OwnerProfile.CREATOR_NAME; textSize = 22f })
        column.addView(TextView(this).apply {
            text = OwnerProfile.CREATOR_TITLE
            textSize = 13f
            setTextColor(ContextCompat.getColor(context, R.color.studio_text_secondary))
        })
        column.addView(TextView(this).apply {
            text = OwnerProfile.CREATOR_COUNTRY + " · Creator of " + OwnerProfile.PRODUCT_NAME
            textSize = 13f
            setPadding(0, 4, 0, 12)
            setTextColor(ContextCompat.getColor(context, R.color.studio_text_secondary))
        })

        column.addView(TextView(this).apply {
            text = OwnerProfile.CREATOR_BIO
            textSize = 14f
            setPadding(0, 8, 0, 12)
        })

        column.addView(TextView(this).apply { text = "Core Focus"; textSize = 16f; setPadding(0, 12, 0, 6) })
        OwnerProfile.CORE_FOCUS.forEach { focus ->
            column.addView(TextView(this).apply { text = "• $focus"; textSize = 14f; setPadding(0, 2, 0, 2) })
        }

        column.addView(TextView(this).apply { text = "Official Links"; textSize = 16f; setPadding(0, 16, 0, 6) })
        column.addView(linkButton("Portfolio — arian00001.site", OwnerProfile.PORTFOLIO_URL))
        column.addView(linkButton("Instagram — @dev_ayan00001", OwnerProfile.INSTAGRAM_URL))
        column.addView(linkButton("Telegram — DevStyleHQ", OwnerProfile.TELEGRAM_URL))
        OwnerProfile.WHATSAPP_NUMBER?.let { number ->
            column.addView(linkButton("WhatsApp — $number", "https://wa.me/" + number.replace("+", "")))
        }

        column.addView(TextView(this).apply {
            text = "Motto: " + OwnerProfile.MOTTO
            textSize = 14f
            setPadding(0, 16, 0, 12)
        })
        column.addView(TextView(this).apply {
            text = OwnerProfile.COPYRIGHT_NOTICE
            textSize = 11f
            setTextColor(ContextCompat.getColor(context, R.color.studio_text_secondary))
        })

        val scroll = ScrollView(this).apply {
            addView(column)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(scroll)
            addView(Button(this@DeveloperProfileActivity).apply {
                text = "Close"
                setOnClickListener { finish() }
            })
        }
    }

    private fun linkButton(label: String, url: String): Button = Button(this).apply {
        text = label
        setOnClickListener {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            } catch (_: Exception) {
                // No browser installed — nothing to open.
            }
        }
    }
}