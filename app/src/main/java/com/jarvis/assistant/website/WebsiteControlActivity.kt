package com.jarvis.assistant.website

import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.jarvis.assistant.JarvisApp
import com.jarvis.assistant.lock.MonoUi
import com.jarvis.assistant.security.CreatorAuthenticationService
import kotlinx.coroutines.launch

/**
 * Creator-only website console.
 *
 * Reached by three quick taps on the profile photo in Settings, so a normal user
 * never sees a creator key field. The pass is verified by the website itself, and
 * every admin call carries the saved agent key.
 */
class WebsiteControlActivity : AppCompatActivity() {

    private lateinit var column: LinearLayout
    private lateinit var status: TextView
    private var headerViews = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        column = MonoUi.page(
            this,
            "Website control",
            "Jarvis's own website. Read anything; change it once the creator pass is verified."
        )
        headerViews = column.childCount
        status = MonoUi.text(this, "").apply { setTextColor(MonoUi.DIM) }
        render()
    }

    private fun render() {
        while (column.childCount > headerViews) column.removeViewAt(column.childCount - 1)
        if (CreatorAuthenticationService.isCreator(this)) renderConsole() else renderGate()
    }

    private fun renderGate() {
        column.addView(MonoUi.label(this, "Creator verification"))
        column.addView(
            MonoUi.text(this, "Enter the creator pass. The website checks it - a wrong pass unlocks nothing on this phone.")
                .apply { setTextColor(MonoUi.DIM) }
        )

        val passInput = input("Creator pass", secret = true)
        column.addView(passInput)

        status = MonoUi.text(this, "").apply { setTextColor(MonoUi.DIM) }
        column.addView(MonoUi.button(this, "VERIFY CREATOR PASS") {
            verify(passInput.text.toString().trim())
        })
        column.addView(status)

        column.addView(
            MonoUi.footer(
                this,
                "This screen opens only after three quick taps on your profile photo in Settings, so other users never " +
                    "see a creator key field. The pass is never stored - only the verified state and the agent key stay on this phone."
            )
        )
    }

    private fun verify(pass: String) {
        if (pass.isBlank()) {
            status.text = "Enter the creator pass first."
            return
        }
        status.text = "Checking with the website..."
        lifecycleScope.launch {
            val reply = WebsiteApi.creatorVerify(pass)
            val verified = WebsiteApi.flag(reply, "verified")
            if (reply.ok && verified) {
                CreatorAuthenticationService.markCreatorVerified(this@WebsiteControlActivity)
                val name = WebsiteApi.field(reply, "creator")
                if (name.isNotBlank()) JarvisApp.instance.preferences.creatorName = name
                status.setTextColor(MonoUi.WHITE)
                status.text = "Creator verified" + if (name.isNotBlank()) " - " + name + "." else "."
                render()
            } else {
                status.setTextColor(MonoUi.DIM)
                status.text = when {
                    reply.code == 401 -> "Pass did not match."
                    reply.body.isNotBlank() -> "Website said: " + reply.body.take(220)
                    else -> "Cannot reach the website: " + (reply.error ?: "unknown error")
                }
            }
        }
    }

    private fun renderConsole() {
        val name = JarvisApp.instance.preferences.creatorName

        column.addView(MonoUi.label(this, "Creator access"))
        column.addView(
            MonoUi.text(this, "Creator verified" + if (name.isNotBlank()) " - " + name + "." else ".")
        )

        val keyInput = input("Agent key from the Integration page", secret = false, value = WebsiteStore.agentKey(this))
        column.addView(keyInput)
        column.addView(MonoUi.button(this, "SAVE AGENT KEY") {
            WebsiteStore.setAgentKey(this@WebsiteControlActivity, keyInput.text.toString())
            status.setTextColor(MonoUi.WHITE)
            status.text = if (WebsiteStore.agentKey(this@WebsiteControlActivity).isBlank()) {
                "Agent key cleared. Admin actions are off."
            } else {
                "Agent key saved on this phone."
            }
        })
        column.addView(status)

        column.addView(MonoUi.label(this, "Live website"))
        column.addView(MonoUi.actionRow(this, "Dashboard", "Users, devices, downloads and reports") { fetch("dashboard") })
        column.addView(MonoUi.actionRow(this, "Download stats", "Public counters from the website") { fetch("stats") })
        column.addView(MonoUi.actionRow(this, "Site health", "Is the API awake right now") { fetch("health") })
        column.addView(MonoUi.actionRow(this, "Devices", "Every registered install and its status") { fetch("devices") })
        column.addView(MonoUi.actionRow(this, "Latest release", "What the site serves today") { fetch("latestRelease") })

        column.addView(MonoUi.label(this, "Session"))
        column.addView(MonoUi.button(this, "LOCK CREATOR MODE") {
            CreatorAuthenticationService.revoke(this@WebsiteControlActivity)
            render()
        })

        column.addView(
            MonoUi.footer(
                this,
                "Admin calls carry the agent key in the x-admin-token header and go only to this website's admin API. " +
                    "Jarvis answers website questions in chat and voice with the same live data."
            )
        )
    }

    private fun fetch(action: String) {
        status.setTextColor(MonoUi.WHITE)
        status.text = "Calling the website..."
        lifecycleScope.launch {
            val result = WebsiteControl.run(this@WebsiteControlActivity, action)
            status.text = result.optString("message").ifBlank { "No response." }
        }
    }

    private fun input(hintText: String, secret: Boolean, value: String = ""): EditText = EditText(this).apply {
        hint = hintText
        setText(value)
        setSingleLine()
        textSize = 15f
        setTextColor(MonoUi.WHITE)
        setHintTextColor(MonoUi.FAINT)
        inputType = if (secret) {
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        } else {
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        }
        setPadding(MonoUi.dp(this@WebsiteControlActivity, 16), MonoUi.dp(this@WebsiteControlActivity, 14),
            MonoUi.dp(this@WebsiteControlActivity, 16), MonoUi.dp(this@WebsiteControlActivity, 14))
        background = MonoUi.panel(this@WebsiteControlActivity, 18)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = MonoUi.dp(this@WebsiteControlActivity, 12) }
    }
}
