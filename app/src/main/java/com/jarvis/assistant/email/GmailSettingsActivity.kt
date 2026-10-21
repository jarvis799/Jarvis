package com.jarvis.assistant.email

import android.graphics.Color
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.jarvis.assistant.util.ThemeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * DEV STYLE: Gmail IMAP connection screen. Saving the address + 16-character
 * Google App Password instantly arms the 24/7 background auto reply worker.
 * White minimal layout with lime-green accents, same as the rest of Jarvis.
 */
class GmailSettingsActivity : AppCompatActivity() {

    private val lime = Color.parseColor("#65A30D")
    private val ink = Color.parseColor("#1F2937")
    private val muted = Color.parseColor("#6B7280")
    private val border = Color.parseColor("#E5E7EB")

    private lateinit var emailInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var templateInput: EditText
    private lateinit var keywordsInput: EditText
    private lateinit var statusText: TextView
    private lateinit var accountText: TextView
    private lateinit var saveButton: MaterialButton
    private lateinit var testButton: MaterialButton
    private lateinit var toggleButton: MaterialButton
    private lateinit var rulesButton: MaterialButton
    private lateinit var disconnectButton: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyTheme(this)
        super.onCreate(savedInstanceState)
        setContentView(buildUi())
        refreshUi()
    }

    private fun px(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun card(view: View): View {
        view.background = GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = px(18).toFloat(); setStroke(px(1), border) }
        return view
    }

    private fun title(text: String): TextView = TextView(this).apply {
        this.text = text; setTextColor(ink); textSize = 15f; setTypeface(typeface, android.graphics.Typeface.BOLD)
    }

    private fun hint(text: String): TextView = TextView(this).apply {
        this.text = text; setTextColor(muted); textSize = 12.5f; setLineSpacing(0f, 1.15f)
    }

    private fun input(hintText: String, secret: Boolean, prefill: String, multiline: Boolean = false): EditText = EditText(this).apply {
        hint = hintText
        setText(prefill)
        textSize = 14f
        setTextColor(ink)
        setHintTextColor(muted)
        background = GradientDrawable().apply { setColor(Color.parseColor("#F7FBEF")); cornerRadius = px(12).toFloat(); setStroke(px(1), border) }
        setPadding(px(14), px(11), px(14), px(11))
        inputType = when {
            secret -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            multiline -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            else -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        }
        if (multiline) { minLines = 4; gravity = Gravity.TOP or Gravity.START }
    }

    private fun limeButton(text: String, filled: Boolean): MaterialButton = MaterialButton(this).apply {
        this.text = text
        if (filled) {
            backgroundTintList = ColorStateList.valueOf(lime)
            setTextColor(Color.WHITE)
        } else {
            backgroundTintList = ColorStateList.valueOf(Color.WHITE)
            setTextColor(lime)
            strokeColor = ColorStateList.valueOf(lime)
            strokeWidth = px(1)
        }
        cornerRadius = px(14)
        textSize = 13.5f
        setTypeface(typeface, android.graphics.Typeface.BOLD)
    }

    private fun buildUi(): View {
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(18), px(20), px(18), px(24))
        }
        val scroll = ScrollView(this).apply { addView(column) }
        scroll.setBackgroundColor(Color.WHITE)

        column.addView(TextView(this).apply {
            text = "Gmail 24/7 Auto Reply"
            setTextColor(ink); textSize = 22f; setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        column.addView(hint("IMAP + SMTP over SSL - App Password only. App password save korar shathe shathe 24/7 auto reply ON hoye jabe. Credentials Android Keystore diye encrypted thake, kokhono server e jay na.").apply { setPadding(0, px(8), 0, px(4)) })

        statusText = TextView(this).apply { text = ""; setTextColor(Color.parseColor("#16A34A")); textSize = 14f; setTypeface(typeface, android.graphics.Typeface.BOLD) }
        accountText = hint("")
        val statusCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(px(16), px(14), px(16), px(14))
            addView(statusText); addView(accountText)
        }
        column.addView(card(statusCard), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = px(16) })

        column.addView(title("Gmail address").apply { setPadding(0, px(18), 0, px(6)) })
        emailInput = input("yourname@gmail.com", false, GmailStore.email(this))
        column.addView(emailInput)

        column.addView(title("Google App Password (16 characters)").apply { setPadding(0, px(14), 0, px(6)) })
        passwordInput = input("xxxxxxxxxxxxxxxx", true, "")
        column.addView(passwordInput)
        column.addView(hint("Google Account > Security > 2-Step Verification > App passwords theke nien - na Gmail password.").apply { setPadding(0, px(6), 0, 0) })

        saveButton = limeButton("Save - 24/7 Auto Reply ON", true)
        column.addView(saveButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = px(16) })
        saveButton.setOnClickListener { saveAndConnect() }

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        testButton = limeButton("Test Gmail", false)
        toggleButton = limeButton("Pause 24/7", false)
        row.addView(testButton, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { weight = 1f; marginEnd = px(8) })
        row.addView(toggleButton, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = px(8) })
        column.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = px(10) })
        testButton.setOnClickListener { runTest() }
        toggleButton.setOnClickListener { toggleAutoReply() }

        column.addView(title("Auto reply rules").apply { setPadding(0, px(22), 0, px(6)) })
        column.addView(hint("Template e {name} thakle sender er name bosbe. Keywords dile sudhu matching email gulote reply jabe - faka rakhle sob unkown personal email e jay (bulk/newsletter automatic skip)."))
        templateInput = input("Reply template", false, GmailStore.template(this), multiline = true)
        column.addView(templateInput, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = px(10) })
        keywordsInput = input("Optional keywords: work, urgent, project", false, GmailStore.keywords(this).joinToString(", "))
        column.addView(keywordsInput, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = px(10) })
        rulesButton = limeButton("Save rules", false)
        column.addView(rulesButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = px(12) })
        rulesButton.setOnClickListener {
            GmailStore.setTemplate(this, templateInput.text.toString().trim())
            GmailStore.setKeywords(this, keywordsInput.text.toString().trim())
            Toast.makeText(this, "Rules saved. Next inbox scan e kaj korbe.", Toast.LENGTH_SHORT).show()
        }

        disconnectButton = limeButton("Disconnect Gmail", false)
        disconnectButton.setTextColor(Color.parseColor("#EF4444"))
        disconnectButton.strokeColor = ColorStateList.valueOf(Color.parseColor("#EF4444"))
        column.addView(disconnectButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = px(22) })
        disconnectButton.setOnClickListener {
            GmailStore.clear(this)
            GmailAutoReplyWorker.cancel(this)
            Toast.makeText(this, "Gmail disconnected, 24/7 auto reply OFF.", Toast.LENGTH_SHORT).show()
            refreshUi()
        }

        return scroll
    }

    private fun saveAndConnect() {
        val email = emailInput.text.toString().trim()
        val password = passwordInput.text.toString()
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(this, "Thik Gmail address din.", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            GmailStore.saveCredentials(this, email, password)
        } catch (e: IllegalArgumentException) {
            Toast.makeText(this, e.message ?: "Invalid App Password.", Toast.LENGTH_LONG).show()
            return
        }
        GmailAutoReplyWorker.schedule(this)
        passwordInput.setText("")
        Toast.makeText(this, "Saved. 24/7 Auto Reply is ON.", Toast.LENGTH_LONG).show()
        refreshUi()
    }

    private fun runTest() {
        testButton.isEnabled = false
        testButton.text = "Testing..."
        lifecycleScope.launch {
            val result = try {
                GmailClient.testConnection(this@GmailSettingsActivity)
            } catch (e: Exception) {
                "Failed: " + (e.message ?: e.javaClass.simpleName)
            }
            withContext(Dispatchers.Main) {
                testButton.isEnabled = true
                testButton.text = "Test Gmail"
                MaterialAlertDialogBuilder(this@GmailSettingsActivity).setTitle("Gmail Connection Test").setMessage(result).setPositiveButton("OK", null).show()
            }
        }
    }

    private fun toggleAutoReply() {
        val nowEnabled = !GmailStore.autoReplyEnabled(this)
        GmailStore.setAutoReplyEnabled(this, nowEnabled)
        if (nowEnabled) GmailAutoReplyWorker.schedule(this) else GmailAutoReplyWorker.cancel(this)
        refreshUi()
    }

    private fun refreshUi() {
        val connected = GmailStore.isConnected(this)
        val enabled = GmailStore.autoReplyEnabled(this)
        if (connected) {
            statusText.text = "CONNECTED - 24/7 AUTO REPLY " + (if (enabled) "ON" else "PAUSED")
            statusText.setTextColor(if (enabled) Color.parseColor("#16A34A") else Color.parseColor("#D97706"))
            accountText.text = GmailStore.email(this)
            emailInput.setText(GmailStore.email(this))
            templateInput.setText(GmailStore.template(this))
            keywordsInput.setText(GmailStore.keywords(this).joinToString(", "))
            toggleButton.text = if (enabled) "Pause 24/7" else "Resume 24/7"
            toggleButton.isEnabled = true
            disconnectButton.isEnabled = true
        } else {
            statusText.text = "NOT CONNECTED"
            statusText.setTextColor(Color.parseColor("#EF4444"))
            accountText.text = "Address ar App Password save korun - taile Jarvis apnar inbox 24/7 monitor korbe."
            toggleButton.isEnabled = false
            disconnectButton.isEnabled = false
        }
    }
}