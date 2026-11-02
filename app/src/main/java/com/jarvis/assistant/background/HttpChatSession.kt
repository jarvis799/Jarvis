package com.jarvis.assistant.background

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import com.jarvis.assistant.data.model.ConversationState
import com.jarvis.assistant.network.EndpointConfig
import com.jarvis.assistant.voice.BargeInMonitor
import com.jarvis.assistant.voice.SpeechChunker
import com.jarvis.assistant.voice.SpeechConfidencePolicy
import com.jarvis.assistant.voice.StopCommandMatcher
import com.jarvis.assistant.voice.VoiceTurnCoordinator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * HTTPS voice conversation with a real-time speaking pipeline.
 *
 * What changed versus the previous turn loop (whole-answer-at-once):
 *  1. the final LLM answer is requested as a stream, so the first sentence is spoken while
 *     the rest is still being generated;
 *  2. [SpeechChunker] turns deltas into speakable phrases (never mid-word, Bengali/Hindi
 *     sentence marks included);
 *  3. [StreamingSpeechOutput] plays those phrases back-to-back with a bounded queue;
 *  4. the microphone stays open while Jarvis speaks ([BargeInMonitor]) so genuine user
 *     speech interrupts immediately;
 *  5. every callback is tagged with a [VoiceTurnCoordinator] token - an interrupted turn
 *     cannot speak, resume, or mutate UI afterwards;
 *  6. transcripts are gated by [SpeechConfidencePolicy]: unclear speech triggers a bounded
 *     clarification instead of a fabricated command;
 *  7. STOP is handled by [StopCommandMatcher] before the text reaches the LLM.
 *
 * Preserved behaviour: creator phrase, standby hand-off, identity answers, unlock request,
 * AgentEngine command routing, mute, pause/resume, and every existing callback signature.
 */
class HttpChatSession(
    private val context: Context, parentScope: CoroutineScope,
    private val endpoint: String, private val apiKey: String, private val model: String,
    private val language: String, private val allowVoice: Boolean,
    private val onStandby: (String) -> Boolean,
    private val onState: (ConversationState) -> Unit, private val onUser: (String) -> Unit,
    private val onReply: (String, String) -> Unit, private val onLevel: (Float) -> Unit,
    private val onError: (String) -> Unit
) {
    companion object {
        private const val TAG = "HttpChatSession"
    }

    private val scope = CoroutineScope(
        parentScope.coroutineContext + SupervisorJob(parentScope.coroutineContext[Job]) + Dispatchers.Main.immediate
    )
    private val prompts = Channel<String>(8)

    private val speech = StreamingSpeechOutput(context, scope, onError)
    private val turns = VoiceTurnCoordinator()
    private val chunker = SpeechChunker()
    private val confidence = SpeechConfidencePolicy()
    private val bargeIn = BargeInMonitor { amplitude -> onBargeInDetected(amplitude) }
    private val speakingStarted = AtomicBoolean(false)

    private var recognizer: SpeechRecognizer? = null
    private var listening = false
    // Consecutive recoverable recognition failures. Reset the moment a turn is understood.
    private var recognitionFailures = 0
    // Set after a language-pack error: the next attempt drops the chosen language and uses the
    // device default, which is what makes voice survive on phones without that offline pack.
    private var useDeviceLanguage = false
    private var working = false
    private var muted = false
    private var closed = false
    private var retry: Job? = null
    private var activeRequest: Deferred<String>? = null
    private var activeWriter: Job? = null

    val isSpeechPaused: Boolean get() = speech.isPaused

    fun start(initiallyMuted: Boolean) {
        muted = initiallyMuted
        if (allowVoice && SpeechRecognizer.isRecognitionAvailable(context)) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) { if (listening && !closed) onState(ConversationState.LISTENING) }
                    override fun onBeginningOfSpeech() = Unit
                    override fun onRmsChanged(rmsdB: Float) { if (listening && !closed) onLevel((rmsdB / 12f).coerceIn(0f, 1f)) }
                    override fun onBufferReceived(buffer: ByteArray?) = Unit
                    override fun onEndOfSpeech() { if (listening && !closed) onState(ConversationState.THINKING) }
                    override fun onPartialResults(partialResults: Bundle?) = Unit
                    override fun onEvent(eventType: Int, params: Bundle?) = Unit
                    override fun onError(error: Int) { handleRecognitionError(error) }
                    override fun onResults(results: Bundle?) { handleRecognitionResults(results) }
                })
            }
        } else onError(
            if (!allowVoice) "Owner verification is enabled: use Gemini for verified voice, or type in HTTPS chat."
            else "Install an Android speech recognition service, or type in chat."
        )

        scope.launch {
            listen()
            for (text in prompts) {
                working = true
                cancelListening()
                onLevel(0f)

                val verifier = com.jarvis.assistant.security.CreatorVerifier
                val isCreator = verifier.isCreatorPhrase(text)
                if (isCreator) {
                    com.jarvis.assistant.security.CreatorAuthenticationService.markCreatorVerified(context)
                }
                val displayText = if (isCreator) "[Creator greeting phrase]" else text
                onUser(displayText)
                if (onStandby(text)) {
                    working = false
                    return@launch
                }
                onState(ConversationState.THINKING)

                val token = turns.begin()
                speech.begin(token)
                chunker.reset()
                speakingStarted.set(false)
                val deltas = Channel<String>(64)

                activeWriter = scope.launch {
                    for (delta in deltas) {
                        for (phrase in chunker.append(delta)) {
                            speakPhrase(phrase, token)
                        }
                    }
                }

                try {
                    val identity = "You are Jarvis. " + com.jarvis.assistant.security.OwnerProfile.IDENTITY_ANSWER_EN +
                        " Brand: " + com.jarvis.assistant.security.OwnerProfile.BRAND +
                        ". Do not claim your app creator trained the underlying model. "
                    val prompt = identity + "Reply naturally in " +
                        EndpointConfig.speechLocale(language, text).displayLanguage + "." +
                        memoryContext() + " User: " + text
                    val question = text.lowercase(java.util.Locale.ROOT).trim().trimEnd('?', '!', '.')
                    val identityQuestions = setOf(
                        "who created you", "who create you", "who made you", "who is your creator",
                        "tomar creator ke", "তোমার নির্মাতা কে", "তোমার ক্রিয়েটর কে", "তোমাকে কে তৈরি করেছে"
                    )
                    val unlock = com.jarvis.assistant.security.UnlockManager

                    val request = scope.async {
                        when {
                            isCreator -> verifier.greeting()
                            unlock.isUnlockCommand(text) -> unlock.requestUnlock(context)
                            question in identityQuestions ->
                                if (EndpointConfig.speechLocale(language, text).language == "bn")
                                    com.jarvis.assistant.security.OwnerProfile.IDENTITY_ANSWER_BN
                                else com.jarvis.assistant.security.OwnerProfile.IDENTITY_ANSWER_EN
                            else -> com.jarvis.assistant.agent.AgentEngine.process(
                                text, prompt, endpoint, apiKey, model, context
                            ) { delta ->
                                if (turns.isCurrent(token)) deltas.trySend(delta)
                            }
                        }
                    }
                    activeRequest = request

                    val reply = request.await()
                    onReply(displayText, reply)
                    rememberTurn(text, reply)

                    deltas.close()
                    activeWriter?.join()
                    activeWriter = null

                    if (turns.isCurrent(token)) {
                        // Non-streaming answers (commands, identity, unlock) have not spoken yet.
                        if (!speakingStarted.get()) {
                            for (phrase in chunker.append(reply)) speakPhrase(phrase, token)
                        }
                        chunker.flush()?.let { speakPhrase(it, token) }
                        speech.finish(token)
                    }
                } catch (e: CancellationException) {
                    // Interrupted by the user: this turn must never speak again.
                    deltas.close()
                    activeWriter?.cancel()
                    activeWriter = null
                    speech.interrupt()
                    if (!isActive || closed) throw e
                    Log.i(TAG, "turn cancelled by interruption")
                } catch (e: Exception) {
                    deltas.close()
                    activeWriter?.cancel()
                    activeWriter = null
                    onError(e.message ?: "Check your own endpoint in Settings.")
                } finally {
                    stopBargeIn()
                    activeRequest = null
                    working = false
                    if (!closed) listen()
                }
            }
        }
    }

    fun sendText(text: String) {
        if (closed || text.isBlank()) return
        if (text.length > 7000) { onError("Please use fewer than 7000 characters."); return }
        if (StopCommandMatcher.isStopCommand(text, workActive = working || speech.isActive)) {
            interrupt()
            return
        }
        if (prompts.trySend(text.trim()).isFailure) onError("Please wait for the current replies.")
    }

    fun setMuted(value: Boolean) {
        muted = value
        if (value) {
            cancelListening()
            onLevel(0f)
            stopBargeIn()
            if (!working) onState(ConversationState.IDLE)
        } else listen()
    }

    /** Stops speech, cancels generation and returns to listening. Safe to call repeatedly. */
    fun interrupt() {
        turns.invalidate()
        activeRequest?.cancel()
        activeRequest = null
        activeWriter?.cancel()
        activeWriter = null
        speech.interrupt()
        stopBargeIn()
        confidence.reset()
        onLevel(0f)
        if (!closed) {
            onState(ConversationState.LISTENING)
            listen()
        }
    }

    fun pauseSpeech() {
        speech.pause()
        if (speech.isPaused) onState(ConversationState.IDLE)
    }

    fun resumeSpeech() {
        if (speech.isPaused) {
            onState(ConversationState.SPEAKING)
            speech.resume()
        }
    }

    fun close() {
        closed = true
        turns.invalidate()
        activeRequest?.cancel()
        activeWriter?.cancel()
        cancelListening()
        stopBargeIn()
        recognizer?.destroy()
        recognizer = null
        prompts.close()
        speech.close()
        scope.cancel()
    }

    // --- speech piping -----------------------------------------------------

    private suspend fun speakPhrase(phrase: String, token: Long) {
        if (phrase.isBlank() || !turns.isCurrent(token)) return
        if (speakingStarted.compareAndSet(false, true)) {
            onState(ConversationState.SPEAKING)
            startBargeIn()
        }
        speech.enqueue(phrase, EndpointConfig.speechLocale(language, phrase), token)
    }

    private fun startBargeIn() {
        if (closed || muted) return
        if (bargeIn.isRunning) return
        if (!bargeIn.start()) return
        if (!bargeIn.hasEchoCancellation) {
            // Without verified echo cancellation the speaker feeds straight back into the
            // microphone and Jarvis would interrupt itself. Off is the honest setting.
            bargeIn.stop()
            Log.i(TAG, "Barge-in unavailable: this device did not enable echo cancellation")
        }
    }

    private fun stopBargeIn() {
        if (bargeIn.isRunning) bargeIn.stop()
    }

    private fun onBargeInDetected(amplitude: Float) {
        // Called from the capture thread.
        if (closed) return
        Log.i(TAG, "user barged in (amp=$amplitude)")
        scope.launch { interrupt() }
    }

    // --- recognition -------------------------------------------------------

    private fun handleRecognitionResults(results: Bundle?) {
        if (!listening || closed) return
        listening = false
        recognitionFailures = 0
        useDeviceLanguage = false

        val alternatives = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
        val scores = results?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)
        val best = alternatives.firstOrNull().orEmpty()

        when (confidence.evaluate(best, scores?.firstOrNull(), alternatives.drop(1))) {
            SpeechConfidencePolicy.Verdict.CLEAR -> {
                confidence.reset()
                if (com.jarvis.assistant.battery.ChargerAudioPlayer.ignoreRecognition) { listen(); return }
                if (best.isNotBlank()) {
                    com.jarvis.assistant.battery.ConversationLanguage.observe(context, best)
                    sendText(best)
                } else listen()
            }
            SpeechConfidencePolicy.Verdict.UNCERTAIN -> {
                if (confidence.shouldAsk()) askToRepeat() else listen()
            }
            // Ambient noise and low-confidence hypotheses never become commands,
            // even after the clarification budget has been exhausted.
            SpeechConfidencePolicy.Verdict.UNINTELLIGIBLE -> listen()
        }
    }

    private fun handleRecognitionError(error: Int) {
        if (!listening || closed) return
        listening = false
        onLevel(0f)

        val silent: IntArray = intArrayOf(
            SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
            SpeechRecognizer.ERROR_CLIENT, SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
            SpeechRecognizer.ERROR_AUDIO
        )
        if (error in silent) {
            // Nothing was said, or the engine was still finishing the previous turn: just
            // listen again without scaring the user with a toast.
            onState(ConversationState.IDLE)
            retryOnce(if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) 600L else 900L)
            return
        }

        // 12 = language not supported, 13 = language pack unavailable: the chosen voice language
        // is what the phone cannot handle. Retry in the device language before giving up.
        if (error == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ||
            error == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED
        ) {
            if (!useDeviceLanguage || recognitionFailures < 2) {
                useDeviceLanguage = true
                recognitionFailures++
                onState(ConversationState.IDLE)
                retryOnce(200L)
                return
            }
        }

        // Server/network hiccups are worth one quiet retry as well - voice must not stop for good
        // because Google's endpoint blinked.
        if (error == SpeechRecognizer.ERROR_SERVER || error == SpeechRecognizer.ERROR_NETWORK ||
            error == SpeechRecognizer.ERROR_NETWORK_TIMEOUT || error == SpeechRecognizer.ERROR_SERVER_DISCONNECTED
        ) {
            if (recognitionFailures < 2) {
                recognitionFailures++
                onState(ConversationState.IDLE)
                retryOnce(1200L)
                return
            }
        }

        onState(ConversationState.IDLE)
        val reason = when (error) {
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is missing for voice input."
            SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> "Android is rate-limiting voice input. Give it a few seconds."
            else -> "Android voice recognition is unavailable here (" + error + "). You can type in chat."
        }
        onError(reason)
    }

    /** Relistens after a short pause, keeping the state machine quiet in between. */
    private fun retryOnce(delayMillis: Long) {
        retry?.cancel()
        retry = scope.launch { delay(delayMillis); listen() }
    }

    /** Speaks a clarification instead of guessing what the user said, then listens again. */
    private fun askToRepeat() {
        val attempt = confidence.attempts
        val message = confidence.clarification(language, attempt)
        confidence.onClarificationSpoken()
        val token = turns.begin()
        speech.begin(token)
        scope.launch {
            onState(ConversationState.SPEAKING)
            speech.enqueue(message, EndpointConfig.speechLocale(language, message), token)
            speech.finish(token)
            if (!closed) listen()
        }
    }

    private fun listen() {
        if (closed || working || listening || speech.isPaused) return
        onLevel(0f)
        if (muted || recognizer == null) { onState(ConversationState.IDLE); return }
        val spokenLanguage = if (useDeviceLanguage) java.util.Locale.getDefault().toLanguageTag()
            else EndpointConfig.speechLocale(language).toLanguageTag()
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, spokenLanguage)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            // Never force the on-device recognizer. Phones without the downloaded language pack
            // answer with error 13 and voice dies completely - the system service is the sane
            // default, with the language fallback below covering the awkward devices.
            // More than one hypothesis is what lets the confidence policy spot a conflict.
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }
        listening = true
        try { recognizer?.startListening(intent) }
        catch (e: Exception) {
            listening = false
            onState(ConversationState.IDLE)
            if (recognitionFailures < 2) {
                recognitionFailures++
                retry?.cancel()
                retry = scope.launch { delay(400); listen() }
            } else onError(e.message ?: "Could not open the microphone.")
        }
    }

    private fun cancelListening() {
        retry?.cancel()
        retry = null
        listening = false
        recognizer?.cancel()
    }

    // --- persistent memory -------------------------------------------------

    private fun memoryContext(): String = runCatching {
        val manager = com.jarvis.assistant.JarvisApp.instance.memoryManager
        val summary = manager.getShortTermContextSummary().trim()
        if (summary.isEmpty()) "" else "\nRecent conversation memory (use only if relevant): " + summary.take(800) + "\n"
    }.getOrDefault("")

    private fun rememberTurn(userText: String, reply: String) {
        runCatching {
            com.jarvis.assistant.JarvisApp.instance.memoryManager.addShortTermTurn(userText, reply)
        }
    }
}