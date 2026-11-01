package com.jarvis.assistant.battery

import android.content.Context
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.jarvis.assistant.JarvisApp

/** Uses only accepted user transcriptions, never assistant output or the microphone itself. */
object ConversationLanguage {
    private var identifying = false
    fun observe(context: Context, text: String) {
        val prefs = context.getSharedPreferences("charger_voice", Context.MODE_PRIVATE)
        val app = JarvisApp.instance.preferences
        if (identifying || app.language != "Auto" || prefs.contains("first_language") || text.trim().length < 12) return
        identifying = true
        val detector = LanguageIdentification.getClient()
        detector.identifyLanguage(text.take(1000)).addOnSuccessListener { code ->
            val label = ChargerPhrases.languages.entries.firstOrNull { it.value.substringBefore('-') == code }?.key
            if (label != null && app.language == "Auto") {
                prefs.edit().putString("first_language", label).apply()
                app.language = label
            }
        }.addOnCompleteListener { identifying = false; detector.close() }
    }
}