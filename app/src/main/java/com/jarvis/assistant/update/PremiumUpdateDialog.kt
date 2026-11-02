package com.jarvis.assistant.update

import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.view.Gravity
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.roundToInt

/** Premium white-glass popup shown whenever a newer JARVIS build is published. */
object PremiumUpdateDialog {

    fun show(activity: Activity, info: ReleaseUpdate.Info) {
        present(activity, info, info.mandatory)
    }

    fun present(activity: Activity, info: ReleaseUpdate.Info, locked: Boolean) {
        if (activity.isFinishing) return
        val density = activity.resources.displayMetrics.density

        fun dp(value: Int): Int = (value * density).roundToInt()

        val dialog = Dialog(activity)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setCancelable(!locked)
        dialog.setCanceledOnTouchOutside(!locked)

        val card = LinearLayout(activity)
        card.orientation = LinearLayout.VERTICAL
        card.setPadding(dp(26), dp(26), dp(26), dp(18))
        val glass = GradientDrawable()
        glass.shape = GradientDrawable.RECTANGLE
        glass.cornerRadius = dp(30).toFloat()
        glass.orientation = GradientDrawable.Orientation.TL_BR
        glass.colors = intArrayOf(Color.parseColor("#FCFFFFFF"), Color.parseColor("#F0F3F7FF"))
        glass.setStroke(dp(1), Color.parseColor("#FFFFFF"))
        card.background = glass

        val pill = TextView(activity)
        pill.text = if (locked) "UPDATE REQUIRED" else "NEW VERSION"
        pill.setTextColor(Color.parseColor("#0B0D12"))
        pill.textSize = 10f
        pill.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        pill.letterSpacing = 0.18f
        pill.gravity = Gravity.CENTER
        pill.setPadding(dp(12), dp(6), dp(12), dp(6))
        val pillBg = GradientDrawable()
        pillBg.cornerRadius = dp(20).toFloat()
        pillBg.setColor(Color.parseColor("#D9FF6A"))
        pill.background = pillBg
        card.addView(pill, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val title = TextView(activity)
        title.text = "JARVIS " + info.versionName.ifBlank { "update" }
        title.setTextColor(Color.parseColor("#0B0D12"))
        title.textSize = 24f
        title.typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        val titleParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        titleParams.topMargin = dp(16)
        card.addView(title, titleParams)

        val body = TextView(activity)
        body.text = info.notes.ifBlank { "A newer build with fixes and improvements is ready to install." }
        body.setTextColor(Color.parseColor("#5A6070"))
        body.textSize = 14f
        body.setLineSpacing(dp(4).toFloat(), 1f)
        val bodyParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        bodyParams.topMargin = dp(8)
        card.addView(body, bodyParams)

        val action = TextView(activity)
        action.text = "Update now"
        action.setTextColor(Color.WHITE)
        action.textSize = 15f
        action.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        action.gravity = Gravity.CENTER
        action.setPadding(dp(18), dp(15), dp(18), dp(15))
        val actionBg = GradientDrawable()
        actionBg.cornerRadius = dp(18).toFloat()
        actionBg.setColor(Color.parseColor("#0B0D12"))
        action.background = actionBg
        action.isClickable = true
        action.setOnClickListener {
            dialog.dismiss()
            val opened = runCatching {
                activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(info.url)))
            }.isSuccess
            if (locked || !opened) activity.finishAffinity()
        }
        val actionParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        actionParams.topMargin = dp(20)
        card.addView(action, actionParams)

        if (!locked) {
            val later = TextView(activity)
            later.text = "Later"
            later.setTextColor(Color.parseColor("#8A90A0"))
            later.textSize = 13f
            later.gravity = Gravity.CENTER
            later.setPadding(dp(8), dp(14), dp(8), dp(4))
            later.isClickable = true
            later.setOnClickListener { dialog.dismiss() }
            val laterParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            laterParams.topMargin = dp(6)
            card.addView(later, laterParams)
        }

        val wrapper = FrameLayout(activity)
        wrapper.setPadding(dp(20), dp(20), dp(20), dp(20))
        wrapper.addView(card, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        dialog.setContentView(wrapper)
        val window = dialog.window
        if (window != null) {
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            window.setDimAmount(0.55f)
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        dialog.show()
    }
}
