package com.jarvis.assistant.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener

/**
 * Plays short TTS samples with the REAL device engine.
 *
 * - One engine instance is reused for the whole screen.
 * - A new preview stops the previous one (QUEUE_FLUSH + stop()).
 * - Audio focus is taken transiently with ducking, so music playing elsewhere is
 *   ducked rather than killed, and focus is released when the sample ends.
 */
class VoicePreviewPlayer(context: Context) {

    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var ready = false
    private var disposed = false
    private var waiting: ((TextToSpeech) -> Unit)? = null
    private var focusRequest: AudioFocusRequest? = null

    var onError: ((String) -> Unit)? = null
    var onFinished: (() -> Unit)? = null
    var onStarted: (() -> Unit)? = null

    /** Runs [action] once the engine is initialised (immediately if already ready). */
    fun withEngine(action: (TextToSpeech) -> Unit) {
        if (disposed) return
        if (ready) {
            tts?.let(action)
            return
        }
        waiting = action
        if (tts != null) return
        tts = TextToSpeech(appContext) { status ->
            if (disposed) return@TextToSpeech
            ready = status == TextToSpeech.SUCCESS
            if (!ready) {
                onError?.invoke("The device has no working Text-to-Speech engine.")
                return@TextToSpeech
            }
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    main.post { onStarted?.invoke() }
                }

                override fun onDone(utteranceId: String?) {
                    main.post {
                        abandonFocus()
                        onFinished?.invoke()
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    main.post {
                        abandonFocus()
                        onError?.invoke("Playback failed for this voice.")
                        onFinished?.invoke()
                    }
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    main.post {
                        abandonFocus()
                        onError?.invoke(errorMessage(errorCode))
                        onFinished?.invoke()
                    }
                }
            })
            waiting?.let { it(tts!!) }
            waiting = null
        }
    }

    /** @return false when the voice cannot be synthesised on this device. */
    fun preview(option: VoiceOption, text: String, style: VoiceStyle? = null): Boolean {
        val engine = tts ?: return false
        if (!ready) return false
        stop()
        val voice = TtsVoiceCatalog.find(engine, option.key) ?: run {
            onError?.invoke("This voice is no longer available on the device.")
            return false
        }
        return try {
            engine.voice = voice
            val result = engine.setLanguage(voice.locale)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                onError?.invoke("No installed TTS data for ${option.languageLabel}. Install the voice data first.")
                return false
            }
            val chosen = style ?: VoiceStyles.current(appContext)
            if (chosen != null) {
                engine.setPitch(chosen.pitch)
                engine.setSpeechRate(chosen.rate)
            }
            requestFocus()
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
            true
        } catch (error: Exception) {
            abandonFocus()
            onError?.invoke(error.message ?: "Could not play this voice.")
            false
        }
    }

    fun speakText(option: VoiceOption, text: String) = preview(option, text)

    fun stop() {
        try {
            tts?.stop()
        } catch (_: Exception) {
        }
        abandonFocus()
    }

    fun release() {
        disposed = true
        stop()
        try {
            tts?.shutdown()
        } catch (_: Exception) {
        }
        tts = null
        ready = false
    }

    private fun requestFocus() {
        val am = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        try {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .build()
            focusRequest = request
            am.requestAudioFocus(request)
        } catch (_: Exception) {
        }
    }

    private fun abandonFocus() {
        val am = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        try {
            focusRequest?.let { am.abandonAudioFocusRequest(it) }
            focusRequest = null
        } catch (_: Exception) {
        }
    }

    private fun errorMessage(code: Int): String = when (code) {
        TextToSpeech.ERROR_NETWORK, TextToSpeech.ERROR_NETWORK_TIMEOUT ->
            "This voice needs internet and the network request failed."
        TextToSpeech.ERROR_NOT_INSTALLED_YET ->
            "The voice data is still downloading on this device."
        TextToSpeech.ERROR_SYNTHESIS -> "The engine could not synthesise this sample."
        else -> "Playback error (code $code)."
    }

    private companion object {
        const val UTTERANCE_ID = "jarvis_voice_preview"
    }
}