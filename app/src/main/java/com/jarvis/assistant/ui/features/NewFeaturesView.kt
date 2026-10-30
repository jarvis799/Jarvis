package com.jarvis.assistant.ui.features

import android.content.Context
import android.content.Intent
import android.util.TypedValue
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.widget.doAfterTextChanged
import com.jarvis.assistant.BuildConfig
import com.jarvis.assistant.R

class NewFeaturesView(context: Context) : LinearLayout(context) {
    init {
        orientation = VERTICAL
        val pad = (16 * resources.displayMetrics.density).toInt()
        setPadding(pad, pad, pad, pad)
        setBackgroundResource(R.drawable.bg_jarvis_card)
        val color = TypedValue().also { context.theme.resolveAttribute(R.attr.themeTextPrimary, it, true) }
        val foreground = if (color.resourceId != 0) context.getColor(color.resourceId) else color.data
        addView(TextView(context).apply {
            text = "NEW FEATURES • v" + BuildConfig.VERSION_NAME + " • build " + BuildConfig.VERSION_CODE
            textSize = 14f
            setTextColor(foreground)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        val search = EditText(context).apply { hint = "Search features"; isSingleLine = true; setTextColor(foreground) }
        addView(search)
        val rows = FeatureCatalog.items.associateWith { feature ->
            TextView(context).apply {
                text = feature.title + "\n" + feature.description
                textSize = 14f
                setTextColor(foreground)
                setPadding(0, pad, 0, pad)
                setBackgroundResource(R.drawable.bg_jarvis_row_item)
                isFocusable = true
                setOnClickListener { context.startActivity(Intent(context, feature.target)) }
                this@NewFeaturesView.addView(this)
            }
        }
        val empty = TextView(context).apply { text = "No matching features."; setTextColor(foreground); visibility = View.GONE }
        addView(empty)
        search.doAfterTextChanged { query ->
            val matches = FeatureCatalog.matching(query?.toString().orEmpty()).toSet()
            rows.forEach { (feature, row) -> row.visibility = if (feature in matches) View.VISIBLE else View.GONE }
            empty.visibility = if (matches.isEmpty()) View.VISIBLE else View.GONE
        }
    }
}
