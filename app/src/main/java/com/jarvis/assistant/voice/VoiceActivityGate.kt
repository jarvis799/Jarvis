package com.jarvis.assistant.voice

/**
 * Frame-level voice activity detection with hysteresis.
 *
 * This is an energy gate, not a speech recogniser: it answers "is a human producing
 * sustained speech right now", which is exactly what barge-in needs. It deliberately does
 * not decide *what* was said, and it does not replace the recogniser or the LLM.
 *
 * Design notes:
 *  - a single loud frame is never enough: [sustainedMs] of consecutive voiced frames is
 *    required to start (default 150 ms, the PRD's initial target);
 *  - while the assistant is speaking the threshold is stricter ([bargeInThreshold]) so
 *    residual speaker echo has to be genuinely loud to interrupt;
 *  - ending requires [silenceHangoverMs] of continuous silence, so natural pauses inside a
 *    sentence do not end the utterance;
 *  - any silent frame resets the start counter, so fan noise and keyboard clicks that are
 *    not sustained cannot open the gate.
 *
 * Thresholds are linear RMS amplitudes (AudioUtils.calculateRmsNormalized scale) and must be
 * tuned on real devices — microphone gain and routing change the absolute levels.
 */
class VoiceActivityGate(
    private val speechThreshold: Float = 0.020f,
    private val bargeInThreshold: Float = 0.045f,
    private val sustainedMs: Int = 150,
    private val frameMs: Int = 20,
    private val silenceHangoverMs: Int = 400
) {

    enum class Event { NONE, SPEECH_STARTED, SPEECH_ENDED }

    private val framesToStart = maxOf(1, sustainedMs / frameMs)
    private val framesToEnd = maxOf(1, silenceHangoverMs / frameMs)

    private var voicedFrames = 0
    private var silentFrames = 0
    private var speaking = false

    val isSpeaking: Boolean get() = speaking

    fun onFrame(amplitude: Float, speakerActive: Boolean): Event {
        val threshold = if (speakerActive) bargeInThreshold else speechThreshold
        val voiced = amplitude >= threshold

        if (!speaking) {
            if (!voiced) {
                voicedFrames = 0
                return Event.NONE
            }
            voicedFrames++
            // Start only after the full sustained window has elapsed, never on its last frame.
            if (voicedFrames <= framesToStart) return Event.NONE
            speaking = true
            voicedFrames = 0
            silentFrames = 0
            return Event.SPEECH_STARTED
        }

        if (voiced) {
            silentFrames = 0
            return Event.NONE
        }

        silentFrames++
        if (silentFrames < framesToEnd) return Event.NONE
        speaking = false
        silentFrames = 0
        voicedFrames = 0
        return Event.SPEECH_ENDED
    }

    fun reset() {
        voicedFrames = 0
        silentFrames = 0
        speaking = false
    }
}