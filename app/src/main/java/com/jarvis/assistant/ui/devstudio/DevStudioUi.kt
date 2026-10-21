package com.jarvis.assistant.ui.devstudio

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.util.TypedValue
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.jarvis.assistant.R

/** Small shared helpers for the Developer Studio screens. */
object DevStudioUi {

    fun toast(context: Context, message: String) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }

    fun openUrl(context: Context, url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            .onFailure { toast(context, "No app can open that link.") }
    }

    fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    fun themeColor(context: Context, attr: Int): Int {
        val typed = TypedValue()
        context.theme.resolveAttribute(attr, typed, true)
        return typed.data
    }

    /** One tappable list row in the app's own card style. */
    fun addRow(parent: LinearLayout, title: String, subtitle: String, onClick: (() -> Unit)? = null): View {
        val context = parent.context
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 12), dp(context, 10), dp(context, 12), dp(context, 10))
            setBackgroundResource(R.drawable.bg_jarvis_row_item)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            if (onClick != null) {
                isClickable = true
                isFocusable = true
                setOnClickListener { onClick() }
            }
        }
        row.addView(TextView(context).apply {
            text = title
            setTextColor(themeColor(context, R.attr.themeTextPrimary))
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
        })
        if (subtitle.isNotBlank()) {
            row.addView(TextView(context).apply {
                text = subtitle
                setTextColor(themeColor(context, R.attr.themeTextSecondary))
                textSize = 12f
            })
        }
        parent.addView(row)
        return row
    }

    fun emptyNote(parent: LinearLayout, message: String) {
        val context = parent.context
        parent.addView(TextView(context).apply {
            text = message
            setTextColor(themeColor(context, R.attr.themeTextMuted))
            textSize = 12f
            setPadding(0, dp(context, 8), 0, dp(context, 8))
        })
    }
}
