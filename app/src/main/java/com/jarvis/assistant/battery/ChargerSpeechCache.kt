package com.jarvis.assistant.battery

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.io.File

/** Generates WAV files with Android TTS, not microphone recordings. Subsequent events play the files. */
class ChargerSpeechCache(private val context: Context) {
    private val handler = Handler(Looper.getMainLooper())
    private val prefs = context.getSharedPreferences("charger_voice", Context.MODE_PRIVATE)
    private val directory = File(context.filesDir, "charger-voice").apply { mkdirs() }
    private var engine: TextToSpeech? = null
    private var ready = false
    private var key = ""
    private var events: Map<String, String> = emptyMap()
    private var pending: Pair<String, Long>? = null
    init {
        engine = TextToSpeech(context) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) prepare() else report("Android TTS could not start. Check the phone's Text-to-speech settings.")
        }
        engine?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) = Unit
            override fun onError(id: String?) { handler.post { report("Voice could not be saved. Install the selected language's Android TTS voice and retry by selecting the language again.") } }
            override fun onDone(id: String?) { handler.post {
                if (id != null && id.startsWith(key + "_")) {
                    val part = File(directory, "$id.part")
                    if (part.length() > 44 && part.renameTo(File(directory, "$id.wav"))) finish()
                }
            } }
        })
    }
    fun prepare() {
        if (!ready) return
        val tts = engine ?: return
        val locale = ChargerPhrases.locale(com.jarvis.assistant.JarvisApp.instance.preferences.language)
        if (tts.setLanguage(locale) < 0) { report("Install the ${locale.displayLanguage} Android TTS voice to enable charger announcements."); return }
        com.jarvis.assistant.voice.TtsVoiceSelector.applyTo(context, tts)
        if (tts.voice?.locale?.language != locale.language) tts.setLanguage(locale)
        val next = "v1_${locale.language}_${tts.voice?.name.hashCode().toUInt()}"
        if (next != key) { tts.stop(); key = next; pending = null }
        events = ChargerPhrases.all(locale)
        report("Saving ${locale.displayLanguage} charger voice files in the background…")
        events.forEach { (event, text) ->
            val id = "${key}_$event"
            if (!File(directory, "$id.wav").isFile && tts.synthesizeToFile(text, null, File(directory, "$id.part"), id) == TextToSpeech.ERROR)
                report("Android TTS could not save this voice. Check its installed voice pack.")
        }
        finish()
    }
    fun play(event: String) {
        val file = File(directory, "${key}_$event.wav")
        if (file.isFile) ChargerAudioPlayer.play(context, file)
        else { prepare(); pending = event to System.currentTimeMillis() }
    }
    private fun finish() {
        if (events.isNotEmpty() && events.keys.all { File(directory, "${key}_$it.wav").isFile }) report("Charger voice saved · ${com.jarvis.assistant.JarvisApp.instance.preferences.language} · reusable offline audio")
        pending?.let { (event, time) ->
            val file = File(directory, "${key}_$event.wav")
            if (file.isFile && System.currentTimeMillis() - time < 10000) { pending = null; ChargerAudioPlayer.play(context, file) }
        }
    }
    private fun report(value: String) { prefs.edit().putString("status", value).apply() }
}