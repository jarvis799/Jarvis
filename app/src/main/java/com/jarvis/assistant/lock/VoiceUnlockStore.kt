package com.jarvis.assistant.lock

import android.content.Context
import org.json.JSONArray
import java.io.File
import java.util.Locale

/** A phrase is a convenience trigger, never proof of identity. No device credentials stored. */
class VoiceUnlockStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("jarvis_voice_unlock", Context.MODE_PRIVATE)
    fun phrases(): List<String> {
        val array = JSONArray(prefs.getString("custom_phrases", "[]") ?: "[]")
        return (0 until array.length()).map { array.getString(it) }
    }
    fun save(old: String?, phrase: String) {
        require(phrase.trim().length in 2..120) { "Use a phrase between 2 and 120 characters." }
        val all = phrases().toMutableList()
        require(all.none { it != old && normalize(it) == normalize(phrase) }) { "This phrase is already saved." }
        if (old != null) { all.remove(old); sample(old).delete() }
        require(all.size < 10) { "Save up to 10 phrases." }
        all.add(phrase.trim())
        prefs.edit().putString("custom_phrases", JSONArray(all).toString()).apply()
        com.jarvis.assistant.background.JarvisConversationService.refreshVoiceUnlockGrammar()
    }
    fun delete(phrase: String) {
        sample(phrase).delete()
        prefs.edit().putString("custom_phrases", JSONArray(phrases().filterNot { it == phrase }).toString()).apply()
        com.jarvis.assistant.background.JarvisConversationService.refreshVoiceUnlockGrammar()
    }
    fun matches(text: String) = phrases().any { normalize(it) == normalize(text) }
    fun sample(phrase: String): File {
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(phrase.toByteArray())
        val name = digest.joinToString("") { "%02x".format(it) }
        return File(File(context.filesDir, "voice_unlock").apply { mkdirs() }, "$name.m4a")
    }
    fun lockEnabled() = prefs.getBoolean("glass_lock", false)
    fun setLockEnabled(on: Boolean) { prefs.edit().putBoolean("glass_lock", on).apply() }
    fun webEnabled() = prefs.getBoolean("local_web", false)
    fun setWebEnabled(on: Boolean) { prefs.edit().putBoolean("local_web", on).apply() }
    companion object {
        fun normalize(text: String) = text.lowercase(Locale.ROOT)
            .replace(Regex("[^\\p{L}\\p{N}\\p{M}\\s]"), " ").trim().replace(Regex("\\s+"), " ")
    }
}