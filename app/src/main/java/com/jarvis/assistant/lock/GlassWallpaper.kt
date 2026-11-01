package com.jarvis.assistant.lock

import android.content.Context
import android.graphics.*
import android.net.Uri
import java.io.File

/** Bounded private copy: no gallery-wide permission and no persistent public URL. */
object GlassWallpaper {
    private fun file(context: Context) = File(context.filesDir, "jarvis_lock_wallpaper.jpg")
    fun load(context: Context): Bitmap? = BitmapFactory.decodeFile(file(context).absolutePath)
    fun clear(context: Context) { file(context).delete() }
    fun save(context: Context, uri: Uri) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Choose a readable photo." }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1600) sample *= 2
        val decoded = context.contentResolver.openInputStream(uri).use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: error("Cannot read this photo.")
        val mono = Bitmap.createBitmap(decoded.width, decoded.height, Bitmap.Config.ARGB_8888)
        val paint = Paint().apply { colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) }) }
        Canvas(mono).drawBitmap(decoded, 0f, 0f, paint)
        val temporary = File(context.filesDir, "jarvis_lock_wallpaper.tmp")
        temporary.outputStream().use { check(mono.compress(Bitmap.CompressFormat.JPEG, 90, it)) }
        check(temporary.renameTo(file(context))) { "Cannot save wallpaper." }
        decoded.recycle(); mono.recycle()
    }
}