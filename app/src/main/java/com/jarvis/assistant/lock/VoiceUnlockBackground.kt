package com.jarvis.assistant.lock

import android.app.KeyguardManager
import android.content.Context
import com.jarvis.assistant.security.UnlockManager
import org.json.JSONArray

object VoiceUnlockBackground {
    private var lastRequest = 0L
    fun grammar(context: Context, wake: Set<String>): String {
        val custom = if (UnlockManager.isEnabled(context)) VoiceUnlockStore(context).phrases().map { VoiceUnlockStore.normalize(it) } else emptyList()
        return JSONArray((wake + custom).toList() + "[unk]").toString()
    }
    fun handle(context: Context, text: String): Boolean {
        if (!UnlockManager.isEnabled(context) || !VoiceUnlockStore(context).matches(text)) return false
        if (context.getSystemService(KeyguardManager::class.java)?.isKeyguardLocked != true) return false
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastRequest > 4000) { lastRequest = now; UnlockManager.requestUnlock(context) }
        return true
    }
}