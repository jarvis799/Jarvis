package com.jarvis.assistant.ui.voice

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import com.jarvis.assistant.ui.lighting.EdgeLightingService

/**
 * Everything that should appear the moment Jarvis is woken by voice: the pastel
 * edge light and the glass panel with the text input box.
 *
 * Both overlays are gated by the "Display over other apps" permission. That gate
 * used to fail silently, so a successful wake-up looked like nothing happened -
 * now the user is told exactly which switch to turn on.
 */
object WakeOverlays {

    fun show(context: Context, voiceOverlayEnabled: Boolean) {
        val app = context.applicationContext
        EdgeLightingService.show(app)
        if (voiceOverlayEnabled) VoiceOverlayController.show(app)
        if (!Settings.canDrawOverlays(app)) {
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(
                    app,
                    "Allow 'Display over other apps' in Settings so the glass panel and edge light appear on wake-up.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}