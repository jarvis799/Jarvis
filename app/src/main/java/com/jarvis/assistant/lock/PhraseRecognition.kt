package com.jarvis.assistant.lock

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.*
import com.jarvis.assistant.background.JarvisConversationService

/** Short foreground test; continuous background recognition stays in the existing Vosk service. */
class PhraseRecognition(private val activity: Activity) : RecognitionListener {
    private var recognizer: SpeechRecognizer? = null
    private var result: (String) -> Unit = {}
    private var status: (String) -> Unit = {}
    private var level: (Float) -> Unit = {}
    fun start(phrase: String, onResult: (String) -> Unit, onStatus: (String) -> Unit, onLevel: (Float) -> Unit = {}) {
        close(); result = onResult; status = onStatus; level = onLevel
        if (!SpeechRecognizer.isRecognitionAvailable(activity)) { status("Install or enable Android speech recognition first."); return }
        JarvisConversationService.pauseForLocalCapture {
            if (activity.isFinishing || activity.isDestroyed) { close(); return@pauseForLocalCapture }
            try {
                recognizer = if (android.os.Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(activity))
                    SpeechRecognizer.createOnDeviceSpeechRecognizer(activity) else SpeechRecognizer.createSpeechRecognizer(activity)
                recognizer?.setRecognitionListener(this)
                recognizer?.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (phrase.any { it in '\u0980'..'\u09ff' }) "bn-BD" else java.util.Locale.getDefault().toLanguageTag())
                    putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                })
            } catch (error: Exception) { status(error.message ?: "Cannot start microphone."); close() }
        }
    }
    fun close() { recognizer?.destroy(); recognizer = null; level(0f); JarvisConversationService.resumeAfterLocalCapture() }
    override fun onReadyForSpeech(params: Bundle?) { status("Listening… Speak your saved phrase.") }
    override fun onResults(results: Bundle?) {
        val spoken = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
        close(); result(spoken)
    }
    override fun onError(error: Int) { close(); status("Recognition stopped ($error). Check microphone permission or installed speech language, then retry.") }
    override fun onRmsChanged(rmsdB: Float) { level((rmsdB / 12f).coerceIn(0f, 1f)) }
    override fun onBeginningOfSpeech() = Unit
    override fun onBufferReceived(buffer: ByteArray?) = Unit
    override fun onEndOfSpeech() = Unit
    override fun onPartialResults(partialResults: Bundle?) = Unit
    override fun onEvent(eventType: Int, params: Bundle?) = Unit
}