package com.jarvis.assistant.media

/** One song that lives on this device. */
data class MusicTrack(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val uri: String
)
