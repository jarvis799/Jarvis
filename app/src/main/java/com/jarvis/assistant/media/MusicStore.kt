package com.jarvis.assistant.media

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import org.json.JSONArray
import org.json.JSONObject

/**
 * The local music library: the songs on the device plus the playlists and
 * favourite songs kept inside Jarvis. Nothing leaves the device.
 */
class MusicStore(private val context: Context) {

    private val prefs = context.getSharedPreferences("jarvis_music", Context.MODE_PRIVATE)

    fun songs(): List<MusicTrack> {
        val out = ArrayList<MusicTrack>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION
        )
        try {
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                MediaStore.Audio.Media.IS_MUSIC + " != 0",
                null,
                MediaStore.Audio.Media.TITLE + " COLLATE NOCASE ASC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    out.add(
                        MusicTrack(
                            id = id,
                            title = cursor.getString(titleCol) ?: "Unknown",
                            artist = cursor.getString(artistCol) ?: "Unknown artist",
                            album = cursor.getString(albumCol) ?: "",
                            durationMs = cursor.getLong(durationCol),
                            uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id).toString()
                        )
                    )
                }
            }
        } catch (_: Exception) {
        }
        return out
    }

    fun favourites(): List<Long> {
        val raw = prefs.getString(KEY_FAVOURITES, "") ?: ""
        if (raw.isBlank()) return emptyList()
        val ids = ArrayList<Long>()
        for (part in raw.split(",")) {
            val id = part.trim().toLongOrNull()
            if (id != null) ids.add(id)
        }
        return ids
    }

    fun isFavourite(id: Long): Boolean = favourites().contains(id)

    fun toggleFavourite(id: Long): Boolean {
        val ids = favourites().toMutableList()
        val added = if (ids.contains(id)) {
            ids.remove(id)
            false
        } else {
            ids.add(id)
            true
        }
        prefs.edit().putString(KEY_FAVOURITES, ids.joinToString(",")).apply()
        return added
    }

    fun playlists(): Map<String, List<Long>> {
        val raw = prefs.getString(KEY_PLAYLISTS, null) ?: return emptyMap()
        val map = LinkedHashMap<String, List<Long>>()
        try {
            val obj = JSONObject(raw)
            val names = obj.keys()
            while (names.hasNext()) {
                val name = names.next()
                val arr = obj.optJSONArray(name) ?: JSONArray()
                val ids = ArrayList<Long>()
                for (i in 0 until arr.length()) ids.add(arr.optLong(i))
                map[name] = ids
            }
        } catch (_: Exception) {
        }
        return map
    }

    private fun savePlaylists(map: Map<String, List<Long>>) {
        val obj = JSONObject()
        for ((name, ids) in map) {
            val arr = JSONArray()
            for (id in ids) arr.put(id)
            obj.put(name, arr)
        }
        prefs.edit().putString(KEY_PLAYLISTS, obj.toString()).apply()
    }

    fun createPlaylist(name: String): Boolean {
        val clean = name.trim()
        if (clean.isEmpty()) return false
        val map = LinkedHashMap(playlists())
        if (!map.containsKey(clean)) {
            map[clean] = emptyList()
            savePlaylists(map)
        }
        return true
    }

    fun addToPlaylist(name: String, trackId: Long) {
        val map = LinkedHashMap(playlists())
        val ids = ArrayList<Long>(map[name] ?: emptyList())
        if (!ids.contains(trackId)) {
            ids.add(trackId)
            map[name] = ids
            savePlaylists(map)
        }
    }

    fun removeFromPlaylist(name: String, trackId: Long) {
        val map = LinkedHashMap(playlists())
        val ids = ArrayList<Long>(map[name] ?: return)
        ids.remove(trackId)
        map[name] = ids
        savePlaylists(map)
    }

    fun deletePlaylist(name: String) {
        val map = LinkedHashMap(playlists())
        map.remove(name)
        savePlaylists(map)
    }

    private companion object {
        const val KEY_FAVOURITES = "favourites"
        const val KEY_PLAYLISTS = "playlists"
    }
}
