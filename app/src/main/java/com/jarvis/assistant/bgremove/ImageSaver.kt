package com.jarvis.assistant.bgremove

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

/** Saves the finished PNG through MediaStore. The original photo is never touched. */
object ImageSaver {

    fun save(context: Context, png: File, displayName: String): String {
        val safeName = displayName.substringBeforeLast('.').ifBlank { "jarvis_removed" }.replace(Regex("[^A-Za-z0-9_-]"), "_") + ".png"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, safeName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Jarvis")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("MediaStore refused the file")
            resolver.openOutputStream(uri)?.use { it.write(png.readBytes()) }
                ?: throw IllegalStateException("Could not open the gallery entry")
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            return "Saved to Pictures/Jarvis as " + safeName
        }
        val pictures = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: context.filesDir
        val target = File(pictures, safeName)
        target.writeBytes(png.readBytes())
        return "Saved in this app's Pictures folder as " + safeName + " (Android 9 and older need the gallery app to rescan it)"
    }
}
