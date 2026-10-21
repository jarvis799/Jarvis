package com.jarvis.assistant.media

import android.content.Context
import android.content.Intent

/**
 * Plays music inside JARVIS itself: a song stored on the device first, otherwise the
 * in-app media viewer. It never switches to the YouTube app or any other player.
 */
object InAppMediaPlayer {

    data class Outcome(val success: Boolean, val title: String?, val message: String)

    fun playSong(context: Context, query: String, background: Boolean = false): Outcome {
        val clean = query.trim()
        if (clean.isBlank()) return Outcome(false, null, "No song was given.")

        val tracks = runCatching { MusicStore(context).songs() }.getOrDefault(emptyList())
        val words = clean.lowercase().split(Regex("\\s+")).filter { it.length > 2 }
        val match = tracks.firstOrNull { it.title.contains(clean, true) || it.artist.contains(clean, true) }
            ?: tracks.firstOrNull { track -> words.any { w -> track.title.contains(w, true) || track.artist.contains(w, true) } }

        if (match != null) {
            val index = tracks.indexOf(match)
            JarvisMusicPlayer.play(context, tracks, index)
            if (!background) {
                runCatching {
                    context.startActivity(
                        Intent(context, MusicPlayerActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }
            return Outcome(true, match.title, "Playing \"" + match.title + "\" by " + match.artist + " inside JARVIS's own music player.")
        }

        // DEV STYLE: songs come from the phone's own storage and play in the background.
        // A song request never opens a web page or switches to another app.
        val close = tracks.take(5).joinToString(", ") { it.title }
        val message = if (tracks.isEmpty()) {
            "Tomar storage er gan ekhono index hoy nai. Media Library ekbar kholo - Jarvis tomar download kora gan nijer player e bajabe."
        } else {
            "Device e \"" + clean + "\" paowa gelo na. Tomar library te ache: " + close + ". Naam bolo, nijer player e bajabo."
        }
        return Outcome(false, null, message)
    }
}
