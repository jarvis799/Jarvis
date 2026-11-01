package com.jarvis.assistant.lock

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.content.res.ColorStateList
import android.widget.*

class VoicePhraseEditor(private val activity: Activity, private val column: LinearLayout,
    private val withMic: (() -> Unit) -> Unit, private val status: (String) -> Unit) {
    private val store = VoiceUnlockStore(activity)
    private val rows = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
    private val audio = VoiceSampleRecorder(activity)
    private val recognition = PhraseRecognition(activity)
    init { column.addView(MonoUi.button(activity, "Add custom phrase") { edit(null) }); column.addView(rows); render() }
    private fun edit(old: String?) {
        close()
        val input = EditText(activity).apply {
            setText(old.orEmpty()); setTextColor(Color.BLACK); setHintTextColor(Color.GRAY)
            backgroundTintList = ColorStateList.valueOf(Color.BLACK); hint = "My personal unlock phrase"
            filters = arrayOf(android.text.InputFilter.LengthFilter(120))
        }
        val dialog = AlertDialog.Builder(activity, android.R.style.Theme_Material_Light_Dialog_Alert)
            .setTitle(if (old == null) "Add phrase" else "Edit phrase").setView(input)
            .setPositiveButton("Save", null).setNegativeButton("Cancel", null).create()
        dialog.setOnShowListener {
            listOf(AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE).forEach { dialog.getButton(it).setTextColor(Color.BLACK) }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                try { store.save(old, input.text.toString()); render(); dialog.dismiss() }
                catch (error: IllegalArgumentException) { status(error.message ?: "Check the phrase."); dialog.dismiss() }
            }
        }; dialog.show()
    }
    private fun render() {
        rows.removeAllViews()
        if (store.phrases().isEmpty()) rows.addView(MonoUi.text(activity, "No custom phrases yet. Add one above."))
        store.phrases().forEach { phrase ->
            rows.addView(MonoUi.text(activity, phrase, 19f))
            rows.addView(MonoUi.button(activity, "Edit phrase") { edit(phrase) })
            rows.addView(MonoUi.button(activity, "Record voice sample") { close(); withMic { audio.record(phrase, status) } })
            rows.addView(MonoUi.button(activity, "Play saved sample") { close(); audio.play(phrase, status) })
            rows.addView(MonoUi.button(activity, "Test phrase recognition") { close(); withMic {
                recognition.start(phrase, { text -> status(if (store.matches(text)) "Phrase matched: $text. Android authentication is still required." else "Heard: $text — no saved phrase matched.") }, status)
            } })
            rows.addView(MonoUi.button(activity, "Delete phrase and sample") { close(); store.delete(phrase); render() })
        }
    }
    fun close() { audio.close(); recognition.close() }
}