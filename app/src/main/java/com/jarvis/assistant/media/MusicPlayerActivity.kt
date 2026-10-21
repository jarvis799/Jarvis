package com.jarvis.assistant.media

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.jarvis.assistant.R
import com.jarvis.assistant.util.ThemeManager

/** The in-app music screen: songs, favourites and playlists, played inside Jarvis. */
class MusicPlayerActivity : AppCompatActivity() {

    private val store by lazy { MusicStore(this) }
    private val onPlayerUpdate: () -> Unit = { runOnUiThread { renderNowPlaying() } }

    private var songs: List<MusicTrack> = emptyList()
    private var tab = "songs"
    private var dragging = false

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyTheme(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_music_player)

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<View>(R.id.btnTabSongs).setOnClickListener { tab = "songs"; render() }
        findViewById<View>(R.id.btnTabFavourites).setOnClickListener { tab = "favourites"; render() }
        findViewById<View>(R.id.btnTabPlaylists).setOnClickListener { tab = "playlists"; render() }
        findViewById<View>(R.id.btnNewPlaylist).setOnClickListener { askNewPlaylist() }
        findViewById<View>(R.id.btnPlayPause).setOnClickListener { JarvisMusicPlayer.toggle(this) }
        findViewById<View>(R.id.btnNext).setOnClickListener { JarvisMusicPlayer.next(this) }
        findViewById<View>(R.id.btnPrev).setOnClickListener { JarvisMusicPlayer.previous(this) }
        findViewById<View>(R.id.btnFavourite).setOnClickListener { toggleCurrentFavourite() }

        findViewById<SeekBar>(R.id.seekBar).setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, value: Int, fromUser: Boolean) {
                if (fromUser && !dragging) JarvisMusicPlayer.seekTo(value)
            }

            override fun onStartTrackingTouch(bar: SeekBar?) {
                dragging = true
            }

            override fun onStopTrackingTouch(bar: SeekBar?) {
                dragging = false
                JarvisMusicPlayer.seekTo(bar?.progress ?: 0)
            }
        })

        JarvisMusicPlayer.addListener(onPlayerUpdate)
        if (hasAudioPermission()) reload() else requestPermissions(arrayOf(audioPermission()), REQUEST_AUDIO)
    }

    override fun onDestroy() {
        JarvisMusicPlayer.removeListener(onPlayerUpdate)
        super.onDestroy()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_AUDIO && hasAudioPermission()) reload() else Toast.makeText(this, "Music needs access to the songs on this device.", Toast.LENGTH_LONG).show()
    }

    private fun reload() {
        songs = store.songs()
        render()
    }

    private fun hasAudioPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, audioPermission()) == PackageManager.PERMISSION_GRANTED

    private fun audioPermission(): String =
        if (Build.VERSION.SDK_INT >= 33) "android.permission.READ_MEDIA_AUDIO" else Manifest.permission.READ_EXTERNAL_STORAGE

    private fun render() {
        renderNowPlaying()
        skinTabs()
        val container = findViewById<LinearLayout>(R.id.listContainer)
        container.removeAllViews()
        when (tab) {
            "favourites" -> {
                val ids = store.favourites()
                val favourites = songs.filter { ids.contains(it.id) }
                if (favourites.isEmpty()) {
                    container.addView(emptyRow("No favourite songs yet. Tap the star while a song plays."))
                } else {
                    favourites.forEachIndexed { index, item -> container.addView(trackRow(item, favourites, index)) }
                }
            }

            "playlists" -> {
                val playlists = store.playlists()
                if (playlists.isEmpty()) {
                    container.addView(emptyRow("No playlists yet. Tap + Playlist to build one."))
                }
                for ((name, ids) in playlists) {
                    container.addView(playlistRow(name, songs.filter { ids.contains(it.id) }))
                }
            }

            else -> {
                if (songs.isEmpty()) {
                    container.addView(emptyRow("No songs found on this device yet."))
                } else {
                    songs.forEachIndexed { index, item -> container.addView(trackRow(item, songs, index)) }
                }
            }
        }
    }

    private fun skinTabs() {
        tabButton(R.id.btnTabSongs, tab == "songs")
        tabButton(R.id.btnTabFavourites, tab == "favourites")
        tabButton(R.id.btnTabPlaylists, tab == "playlists")
    }

    private fun tabButton(id: Int, active: Boolean) {
        val view = findViewById<TextView>(id)
        view.alpha = if (active) 1f else 0.55f
        view.background = ContextCompat.getDrawable(this, if (active) R.drawable.bg_jarvis_action_card else R.drawable.bg_jarvis_chip)
    }

    private fun trackRow(track: MusicTrack, list: List<MusicTrack>, index: Int): View {
        val minutes = track.durationMs / 60000
        val seconds = (track.durationMs % 60000) / 1000
        val duration = String.format("%d:%02d", minutes, seconds)
        val subtitle = track.artist + "  •  " + duration + if (store.isFavourite(track.id)) "  ★" else ""
        return row(track.title, subtitle, { JarvisMusicPlayer.play(this, list, index) }, { askAddToPlaylist(track) })
    }

    private fun playlistRow(name: String, tracks: List<MusicTrack>): View {
        val subtitle = tracks.size.toString() + if (tracks.size == 1) " song  •  long press to delete" else " songs  •  long press to delete"
        return row(name, subtitle, {
            if (tracks.isNotEmpty()) JarvisMusicPlayer.play(this, tracks, 0) else Toast.makeText(this, "That playlist is empty.", Toast.LENGTH_SHORT).show()
        }, {
            MaterialAlertDialogBuilder(this)
                .setTitle("Delete playlist")
                .setMessage("Delete " + name + "? The songs stay on your device.")
                .setPositiveButton("Delete") { _, _ -> store.deletePlaylist(name); render() }
                .setNegativeButton("Cancel", null)
                .show()
        })
    }

    private fun row(title: String, subtitle: String, onClick: () -> Unit, onLongClick: (() -> Unit)?): View {
        val container = LinearLayout(this)
        container.orientation = LinearLayout.VERTICAL
        container.setPadding(dp(16), dp(12), dp(16), dp(12))
        container.background = ContextCompat.getDrawable(this, R.drawable.bg_jarvis_action_card)
        container.setOnClickListener { onClick() }
        if (onLongClick != null) container.setOnLongClickListener { onLongClick.invoke(); true }
        container.addView(TextView(this).apply {
            text = title
            textSize = 14f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setTextColor(attrColor(R.attr.themeTextPrimary, android.graphics.Color.WHITE))
        })
        container.addView(TextView(this).apply {
            text = subtitle
            textSize = 11f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setTextColor(attrColor(R.attr.themeTextMuted, 0xFF9AA0A6.toInt()))
        })
        val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        params.bottomMargin = dp(8)
        container.layoutParams = params
        return container
    }

    private fun emptyRow(message: String): View = TextView(this).apply {
        text = message
        textSize = 13f
        setPadding(dp(4), dp(18), dp(4), dp(18))
        setTextColor(attrColor(R.attr.themeTextMuted, 0xFF9AA0A6.toInt()))
    }

    private fun renderNowPlaying() {
        val track = JarvisMusicPlayer.current
        findViewById<TextView>(R.id.tvNowTitle).text = track?.title ?: "Nothing playing"
        findViewById<TextView>(R.id.tvNowArtist).text = track?.artist ?: "Pick a song to start"
        findViewById<TextView>(R.id.btnPlayPause).text = if (JarvisMusicPlayer.isPlaying) "❚❚" else "▶"
        findViewById<TextView>(R.id.btnFavourite).text = if (track != null && store.isFavourite(track.id)) "★" else "☆"
        val total = JarvisMusicPlayer.durationMs
        if (total > 0L) {
            val bar = findViewById<SeekBar>(R.id.seekBar)
            bar.max = total.toInt()
            if (!dragging) bar.progress = JarvisMusicPlayer.positionMs.toInt()
        }
    }

    private fun toggleCurrentFavourite() {
        val track = JarvisMusicPlayer.current
        if (track == null) {
            Toast.makeText(this, "Play a song first, then tap the star to save it.", Toast.LENGTH_SHORT).show()
            return
        }
        val added = store.toggleFavourite(track.id)
        Toast.makeText(this, if (added) "Saved to favourites." else "Removed from favourites.", Toast.LENGTH_SHORT).show()
        render()
    }

    private fun askNewPlaylist() {
        val input = EditText(this).apply { hint = "Playlist name"; setSingleLine() }
        MaterialAlertDialogBuilder(this)
            .setTitle("New playlist")
            .setView(input)
            .setPositiveButton("Create") { _, _ ->
                if (store.createPlaylist(input.text.toString())) {
                    tab = "playlists"
                    render()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun askAddToPlaylist(track: MusicTrack) {
        val names = store.playlists().keys.toMutableList()
        val options = names.toMutableList()
        options.add("New playlist…")
        MaterialAlertDialogBuilder(this)
            .setTitle("Add to playlist")
            .setItems(options.toTypedArray()) { _, which ->
                if (which == names.size) {
                    askNewPlaylistFor(track)
                } else {
                    val name = names[which]
                    store.addToPlaylist(name, track.id)
                    Toast.makeText(this, "Added to " + name, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun askNewPlaylistFor(track: MusicTrack) {
        val input = EditText(this).apply { hint = "Playlist name"; setSingleLine() }
        MaterialAlertDialogBuilder(this)
            .setTitle("New playlist")
            .setView(input)
            .setPositiveButton("Create") { _, _ ->
                val name = input.text.toString().trim()
                if (store.createPlaylist(name)) {
                    store.addToPlaylist(name, track.id)
                    Toast.makeText(this, "Added to " + name, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun attrColor(attr: Int, fallback: Int): Int {
        val value = android.util.TypedValue()
        return if (theme.resolveAttribute(attr, value, true)) {
            if (value.resourceId != 0) ContextCompat.getColor(this, value.resourceId) else value.data
        } else fallback
    }

    private companion object {
        const val REQUEST_AUDIO = 91
    }
}
