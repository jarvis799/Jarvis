package com.jarvis.assistant.voice

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import com.jarvis.assistant.background.JarvisConversationService
import com.jarvis.assistant.voice.hotword.HotwordStats

/**
 * A digital-assistant invocation (home-button long press, headset button, lock-screen
 * assist gesture) hands control straight to the existing conversation service.
 * No second AI engine, no second TTS.
 */
class JarvisVoiceInteractionSession(context: Context) : VoiceInteractionSession(context) {

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        val ctx: Context = context
        HotwordStats.recordActivation(ctx)

        val intent = Intent(ctx, JarvisConversationService::class.java)
            .setAction(JarvisConversationService.ACTION_START_CONVERSATION)
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ctx.startForegroundService(intent)
            } else {
                ctx.startService(intent)
            }
        }
        hide()
    }
}
