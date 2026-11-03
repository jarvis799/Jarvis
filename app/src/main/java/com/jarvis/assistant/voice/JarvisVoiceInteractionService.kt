package com.jarvis.assistant.voice

import android.service.voice.VoiceInteractionService
import com.jarvis.assistant.voice.hotword.HotwordStats

/**
 * System-facing assistant entry point. Declaring this service lets Android list JARVIS
 * in Settings > Default apps > Digital assistant; the owner has to pick it there.
 */
class JarvisVoiceInteractionService : VoiceInteractionService() {

    override fun onReady() {
        super.onReady()
        HotwordStats.setEngine(this, HotwordStats.ENGINE_ASSISTANT)
    }

    override fun onShutdown() {
        HotwordStats.setEngine(this, HotwordStats.ENGINE_SOFTWARE)
        super.onShutdown()
    }
}
