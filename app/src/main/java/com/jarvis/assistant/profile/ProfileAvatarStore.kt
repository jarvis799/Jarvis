package com.jarvis.assistant.profile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import java.io.File

/** DEV STYLE: the operator's own profile photo, kept in app storage (never uploaded). */
object ProfileAvatarStore {

    private const val DIR = "profile"
    private const val FILE_NAME = "avatar.jpg"
    private const val MAX_EDGE = 512

    private fun avatarFile(context: Context): File {
        val dir = File(context.filesDir, DIR)
        if (!dir.exists()) dir.mkdirs()
        return File(dir, FILE_NAME)
    }

    fun hasAvatar(context: Context): Boolean = avatarFile(context).exists()

    fun load(context: Context): Bitmap? {
        val file = avatarFile(context)
        if (!file.exists()) return null
        return runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()
    }

    fun save(context: Context, uri: Uri): Boolean {
        val bitmap = decode(context, uri) ?: return false
        val largest = maxOf(bitmap.width, bitmap.height)
        val scaled = if (largest > MAX_EDGE) {
            val ratio = MAX_EDGE.toFloat() / largest
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * ratio).toInt().coerceAtLeast(1),
                (bitmap.height * ratio).toInt().coerceAtLeast(1),
                true
            )
        } else {
            bitmap
        }
        return runCatching {
            avatarFile(context).outputStream().use { out ->
                scaled.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            true
        }.getOrDefault(false)
    }

    fun clear(context: Context) {
        runCatching { avatarFile(context).delete() }
    }

    private fun decode(context: Context, uri: Uri): Bitmap? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                decoder.isMutableRequired = false
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
        }
    }.getOrNull()
}