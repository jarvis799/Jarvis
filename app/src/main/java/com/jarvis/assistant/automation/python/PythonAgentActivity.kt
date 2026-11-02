package com.jarvis.assistant.automation.python

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * DEV STYLE: Python Automation - the user's own scripts, run by real Python inside Jarvis.
 * Paste a bot, give it a name, and Jarvis can run it on demand or automatically in the background.
 */
class PythonAgentActivity : AppCompatActivity() {

    private lateinit var list: LinearLayout
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
            setPadding(dp(18), dp(22), dp(18), dp(18))
        }

        root.addView(TextView(this).apply {
            text = "\u2039"
            setTextColor(Color.WHITE)
            textSize = 26f
            setPadding(0, 0, 0, dp(6))
            setOnClickListener { finish() }
        })

        root.addView(TextView(this).apply {
            text = "Python Automation"
            setTextColor(Color.WHITE)
            textSize = 22f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })

        root.addView(TextView(this).apply {
            text = "Real python3 + pip3 inside Jarvis's own Linux. Paste any script - bots, scrapers, automation - and run it now or on a schedule."
            setTextColor(0xFF9A9A9A.toInt())
            textSize = 12.5f
            setPadding(0, dp(6), 0, dp(14))
        })

        root.addView(filledButton("New Python script") { openEditor(null) }, matchWrap())

        status = TextView(this).apply {
            setTextColor(0xFFBDBDBD.toInt())
            textSize = 12f
            setPadding(0, dp(10), 0, dp(10))
        }
        root.addView(status)

        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(ScrollView(this).apply { addView(list) }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        setContentView(root)
        render()
    }

    private fun render() {
        list.removeAllViews()
        val snippets = PythonSnippetStore.all(this)
        if (snippets.isEmpty()) {
            list.addView(TextView(this).apply {
                text = "No script yet. Tap New Python script, paste your code and save it - then Jarvis can run it from chat too."
                setTextColor(0xFF7E7E7E.toInt())
                textSize = 13f
                setPadding(0, dp(8), 0, dp(8))
            })
            return
        }
        snippets.forEach { snippet -> list.addView(card(snippet), cardParams()) }
    }

    private fun card(snippet: PythonSnippet): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = GradientDrawable().apply {
            setColor(0xFF101010.toInt())
            cornerRadius = dp(18).toFloat()
            setStroke(dp(1), 0x33FFFFFF)
        }
        setPadding(dp(16), dp(14), dp(16), dp(14))

        addView(TextView(this@PythonAgentActivity).apply {
            text = snippet.name
            setTextColor(Color.WHITE)
            textSize = 15.5f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        addView(TextView(this@PythonAgentActivity).apply {
            text = buildString {
                append(if (snippet.autoRun) "Auto-run ON" else "Manual run")
                if (snippet.trigger.isNotBlank()) append(" · trigger: ").append(snippet.trigger)
                if (snippet.lastStatus.isNotBlank()) append("\n").append(snippet.lastStatus.take(120))
            }
            setTextColor(0xFF8C8C8C.toInt())
            textSize = 11.5f
            setPadding(0, dp(4), 0, dp(10))
        })

        addView(LinearLayout(this@PythonAgentActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.START
            addView(filledButton("Run") { runSnippet(snippet) })
            addView(ghostButton("Edit") { openEditor(snippet) })
            addView(ghostButton("Delete") { confirmDelete(snippet) })
        })
    }

    private fun openEditor(existing: PythonSnippet?) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(10), dp(18), dp(6))
        }
        val nameField = field("Script name", existing?.name.orEmpty(), InputType.TYPE_CLASS_TEXT)
        val triggerField = field("Trigger word (optional)", existing?.trigger.orEmpty(), InputType.TYPE_CLASS_TEXT)
        val depsField = field("pip packages (optional, space separated)", existing?.dependencies.orEmpty(), InputType.TYPE_CLASS_TEXT)
        val codeField = field("Python code", existing?.code.orEmpty(), InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE).apply {
            minLines = 7
            setTextIsSelectable(true)
            typeface = android.graphics.Typeface.MONOSPACE
            setTextColor(Color.WHITE)
        }
        val autoRun = CheckBox(this).apply {
            text = "Run automatically in the background"
            isChecked = existing?.autoRun == true
            setTextColor(Color.WHITE)
        }

        container.addView(nameField)
        container.addView(triggerField)
        container.addView(depsField)
        container.addView(codeField)
        container.addView(autoRun)

        AlertDialog.Builder(this)
            .setTitle(if (existing == null) "New Python script" else "Edit script")
            .setView(ScrollView(this).apply { addView(container) })
            .setPositiveButton("Save") { _, _ ->
                val name = nameField.text.toString().trim().ifBlank { "Python script" }
                val code = codeField.text.toString()
                if (code.isBlank()) {
                    status.text = "Not saved: the code is empty."
                    return@setPositiveButton
                }
                PythonSnippetStore.save(
                    this,
                    PythonSnippet(
                        id = existing?.id ?: "",
                        name = name,
                        code = code,
                        dependencies = depsField.text.toString().trim(),
                        trigger = triggerField.text.toString().trim(),
                        autoRun = autoRun.isChecked,
                        lastStatus = existing?.lastStatus.orEmpty(),
                        lastRunAt = existing?.lastRunAt ?: 0L
                    )
                )
                status.text = "Saved. Jarvis can now run \"$name\" from chat."
                render()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun runSnippet(snippet: PythonSnippet) {
        status.text = "Running \"${snippet.name}\" - first run installs the Linux environment and python3."
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { PythonSnippetRunner.runSaved(this@PythonAgentActivity, snippet) }
            status.text = if (result.ok) "Finished: ${snippet.name}" else "Failed: ${snippet.name} (exit ${result.exitCode})"
            AlertDialog.Builder(this@PythonAgentActivity)
                .setTitle(if (result.ok) "Output" else "Error output")
                .setMessage(result.output.takeLast(4000).ifBlank { "(no output)" })
                .setPositiveButton("OK", null)
                .show()
            render()
        }
    }

    private fun confirmDelete(snippet: PythonSnippet) {
        AlertDialog.Builder(this)
            .setTitle("Delete script?")
            .setMessage("\"${snippet.name}\" will be removed from Jarvis.")
            .setPositiveButton("Delete") { _, _ ->
                PythonSnippetStore.delete(this, snippet.id)
                render()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun field(hint: String, value: String, type: Int): EditText = EditText(this).apply {
        this.hint = hint
        setText(value)
        setTextColor(Color.WHITE)
        setHintTextColor(0xFF7A7A7A.toInt())
        inputType = type
        setPadding(0, dp(8), 0, dp(8))
    }

    private fun filledButton(label: String, onClick: () -> Unit): Button = Button(this).apply {
        text = label
        setTextColor(Color.BLACK)
        background = GradientDrawable().apply {
            setColor(Color.WHITE)
            cornerRadius = dp(16).toFloat()
        }
        setOnClickListener { onClick() }
    }

    private fun ghostButton(label: String, onClick: () -> Unit): Button = Button(this).apply {
        text = label
        setTextColor(Color.WHITE)
        background = GradientDrawable().apply {
            setColor(0x1AFFFFFF)
            cornerRadius = dp(16).toFloat()
            setStroke(dp(1), 0x44FFFFFF)
        }
        setOnClickListener { onClick() }
    }

    private fun matchWrap(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

    private fun cardParams(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { bottomMargin = dp(12) }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}