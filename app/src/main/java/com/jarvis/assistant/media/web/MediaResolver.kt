package com.jarvis.assistant.media.web

import android.content.Context
import android.content.Intent

/**
 * DEV STYLE: in-app media hooks. Detects YouTube/Instagram links in any
 * Jarvis request and opens them in the built-in premium media viewer
 * instead of handing the user off to another app.
 */
object MediaResolver {

    sealed class Media {
        data class YouTube(val id: String, val playlist: String?) : Media()
        data class Instagram(val kind: String, val id: String) : Media()
    }

    private val ytVideo = Regex("(?:https?://)?(?:www\\.|m\\.)?(?:youtube\\.com/(?:watch\\?v=|shorts/|live/)|youtu\\.be/)([A-Za-z0-9_-]{6,18})")
    private val ytPlaylist = Regex("(?:https?://)?(?:www\\.)?youtube\\.com/playlist\\?list=([A-Za-z0-9_-]+)")
    private val instagram = Regex("(?:https?://)?(?:www\\.)?instagram\\.com/(?:[A-Za-z0-9_.]+/)?(p|reel|reels|tv)/([A-Za-z0-9_-]+)")

    fun find(text: String): Media? {
        val video = ytVideo.find(text)
        if (video != null) return Media.YouTube(video.groupValues[1], null)
        val playlist = ytPlaylist.find(text)
        if (playlist != null) return Media.YouTube("", playlist.groupValues[1])
        val ig = instagram.find(text)
        if (ig != null) return Media.Instagram(ig.groupValues[1].removeSuffix("s"), ig.groupValues[2])
        return null
    }

    fun embedUrl(m: Media): String = when (m) {
        is Media.YouTube -> if (m.playlist != null)
            "https://www.youtube.com/embed/videoseries?list=" + m.playlist + "&autoplay=1"
        else
            "https://www.youtube.com/embed/" + m.id + "?autoplay=1&playsinline=1&rel=0"
        is Media.Instagram -> "https://www.instagram.com/" + m.kind + "/" + m.id + "/embed/captioned/"
    }

    fun title(m: Media): String = when (m) {
        is Media.YouTube -> if (m.playlist != null) "YouTube Playlist" else "YouTube Player"
        is Media.Instagram -> when (m.kind) {
            "reel" -> "Instagram Reel"
            "tv" -> "Instagram Video"
            else -> "Instagram Post"
        }
    }

    fun pageUrl(m: Media): String = when (m) {
        is Media.YouTube -> if (m.playlist != null) "https://www.youtube.com/playlist?list=" + m.playlist
        else "https://www.youtube.com/watch?v=" + m.id
        is Media.Instagram -> "https://www.instagram.com/" + m.kind + "/" + m.id + "/"
    }

    /** Launches the in-app viewer when the request carries a media link. Returns the spoken confirmation, or null. */
    fun launchIfMedia(context: Context, request: String): String? {
        val media = find(request) ?: return null
        return runCatching {
            context.startActivity(intent(context, media).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            when (media) {
                is Media.YouTube -> "Playing it right here inside Jarvis, Sir."
                is Media.Instagram -> "Opening it right here inside Jarvis, Sir."
            }
        }.getOrNull()
    }

    fun intent(context: Context, media: Media): Intent =
        Intent(context, MediaViewerActivity::class.java)
            .putExtra("embed", embedUrl(media))
            .putExtra("title", title(media))
            .putExtra("page", pageUrl(media))
}
