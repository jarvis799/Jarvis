package com.jarvis.assistant.ui.voice

import com.jarvis.assistant.data.model.ConversationState

/**
 * DEV STYLE: the five visual states shared by the glass overlay and the edge-lighting
 * layer, so both effects always agree on what Jarvis is doing.
 */
enum class VoiceOverlayState(val label: String) {
    WAKE("WAKE DETECTED"),
    LISTENING("LISTENING"),
    PROCESSING("PROCESSING"),
    SPEAKING("SPEAKING"),
    IDLE("IDLE");

    companion object {
        fun from(state: ConversationState): VoiceOverlayState = when (state) {
            ConversationState.LISTENING -> LISTENING
            ConversationState.THINKING -> PROCESSING
            ConversationState.SPEAKING -> SPEAKING
            ConversationState.IDLE -> IDLE
        }
    }
}
