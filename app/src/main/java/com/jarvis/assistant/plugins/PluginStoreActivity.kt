package com.jarvis.assistant.plugins

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * DEV STYLE: Plugin Lab - the dashboard home of the self-evolving plugin
 * framework. Install bundled or remote JSON plugins; they hot-reload instantly.
 */
class PluginStoreActivity : Activity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var statusLine: TextView
    private lateinit var listHost: LinearLayout
    private lateinit var input: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PluginManager.init(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF070B14.toInt())
            setPadding(dp(22), dp(22), dp(22), dp(24))
        }
        root.addView(TextView(this).apply {
            text = "PLUGIN LAB"
            textSize = 26f
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.12f
            setTextColor(0xFFF4F7FF.toInt())
        })
        root.addView(TextView(this).apply {
            text = "Self-evolving - install new skills as plugins, hot-reloaded with no app update"
            textSize = 12f
            setTextColor(0xFF8FA3C8.toInt())
            setPadding(0, dp(4), 0, 0)
        })
        statusLine = TextView(this).apply {
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(0xFF22D3EE.toInt())
            setPadding(0, dp(12), 0, dp(4))
        }
        root.addView(statusLine)

        root.addView(section("Install from JSON or a https:// URL"))
        input = EditText(this).apply {
            hint = "Paste plugin JSON or https://.../plugin.json"
            setTextColor(0xFFF4F7FF.toInt())
            setHintTextColor(0xFF5B6B8C.toInt())
            textSize = 12f
            background = box()
            setPadding(dp(12), dp(10), dp(12), dp(10))
            minLines = 2
            maxLines = 6
            gravity = Gravity.TOP
        }
        root.addView(input, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(10), 0, 0) }
        row.addView(accentButton("Install", 0xFF6C5CE7.toInt()) {
            val text = input.text.toString().trim()
            if (text.isEmpty()) { toast("Paste plugin JSON or a https URL first"); return@accentButton }
            scope.launch {
                statusLine.text = "Installing..."
                if (text.startsWith("{")) {
                    result(PluginManager.install(this@PluginStoreActivity, text))
                } else {
                    val fetched = PluginManager.fetchText(text)
                    if (fetched == null) toast("Could not download that URL")
                    else result(PluginManager.install(this@PluginStoreActivity, fetched))
                }
                input.setText("")
                render()
            }
        })
        row.addView(accentButton("Uninstall", 0xFF1C2436.toInt()) {
            val installed = PluginManager.installed()
            if (installed.isEmpty()) { toast("Nothing installed yet"); return@accentButton }
            MaterialAlertDialogBuilder(this@PluginStoreActivity)
                .setTitle("Uninstall a plugin")
                .setItems(installed.map { it.name + "  v" + it.version }.toTypedArray()) { _, which ->
                    val target = installed[which]
                    PluginManager.uninstall(this@PluginStoreActivity, target.id)
                    toast(target.name + " uninstalled")
                    render()
                }
                .setPositiveButton("Close", null)
                .show()
        })
        root.addView(row)

        root.addView(section("Built-in catalog - one tap to install"))
        val scroll = ScrollView(this)
        listHost = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(listHost)
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        setContentView(root)
        render()
    }

    private fun render() {
        val count = PluginManager.reload(this)
        statusLine.text = count.toString() + " plugin(s) active - hot-reload on"
        listHost.removeAllViews()
        for ((title, json) in BundledPlugins.catalog) {
            val manifest = runCatching { PluginCodec.parse(json) }.getOrNull() ?: continue
            val isInstalled = PluginManager.isInstalled(manifest.id)
            listHost.addView(card(
                manifest.name,
                manifest.description,
                if (isInstalled) "INSTALLED" else "INSTALL",
                isInstalled
            ) {
                if (!isInstalled) {
                    result(PluginManager.install(this, json))
                    render()
                }
            })
        }
        val installed = PluginManager.installed()
        if (installed.isNotEmpty()) {
            listHost.addView(section("Installed and live"))
            for (m in installed) {
                listHost.addView(card(m.name, m.description + "  -  " + m.commands.size + " command(s)", "ACTIVE", true, null))
            }
        }
    }

    private fun result(r: Pair<Boolean, String>) {
        if (r.first) toast(r.second) else toast("Failed: " + r.second)
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    private fun section(label: String): TextView = TextView(this).apply {
        text = label
        textSize = 13f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(0xFFCBD5F0.toInt())
        setPadding(0, dp(18), 0, dp(8))
    }

    private fun card(title: String, desc: String, chip: String, active: Boolean, onClick: (() -> Unit)?): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply { setColor(0xFF101828.toInt()); cornerRadius = dp(18f).toFloat().toFloat() }
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }
        card.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(TextView(this@PluginStoreActivity).apply {
                text = title
                textSize = 15f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(0xFFF4F7FF.toInt())
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
            addView(TextView(this@PluginStoreActivity).apply {
                text = chip
                textSize = 10.5f
                typeface = Typeface.DEFAULT_BOLD
                letterSpacing = 0.08f
                setTextColor(if (active) 0xFF22D3EE.toInt() else 0xFF070B14.toInt())
                background = GradientDrawable().apply {
                    setColor(if (active) 0x2A22D3EE else 0xFF6C5CE7.toInt())
                    cornerRadius = dp(999f).toFloat().toFloat()
                }
                setPadding(dp(10), dp(4), dp(10), dp(4))
            })
        })
        card.addView(TextView(this).apply {
            text = desc
            textSize = 12f
            setTextColor(0xFF8FA3C8.toInt())
            setPadding(0, dp(6), 0, 0)
        })
        if (onClick != null) card.setOnClickListener { onClick() }
        val wrap = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 0, 0, dp(10)) }
        wrap.addView(card)
        return wrap
    }

    private fun accentButton(label: String, color: Int, onClick: () -> Unit): TextView = TextView(this).apply {
        text = label
        textSize = 13f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(0xFFFFFFFF.toInt())
        background = GradientDrawable().apply { setColor(color); cornerRadius = dp(999f).toFloat().toFloat() }
        setPadding(dp(18), dp(9), dp(18), dp(9))
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(10) }
    }

    private fun box(): GradientDrawable = GradientDrawable().apply {
        setColor(0xFF101828.toInt())
        cornerRadius = dp(12f).toFloat().toFloat()
        setStroke(dp(1), 0xFF24304A.toInt())
    }

        private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun dp(v: Float): Int = (v * resources.displayMetrics.density).toInt()
}
