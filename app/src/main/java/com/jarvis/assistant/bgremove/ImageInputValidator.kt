package com.jarvis.assistant.bgremove

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns

data class ImageInput(val uri: Uri, val displayName: String, val mimeType: String, val sizeBytes: Long)

sealed class InputCheck {
    data class Ok(val input: ImageInput) : InputCheck()
    data class Rejected(val message: String) : InputCheck()
}

/** File type and size checks. The original file is only ever read, never modified. */
object ImageInputValidator {

    const val MAX_BYTES = 12L * 1024 * 1024
    val SUPPORTED = setOf("image/png", "image/jpeg", "image/jpg", "image/webp")

    fun isSupportedMime(mime: String?): Boolean = mime != null && SUPPORTED.contains(mime.trim().lowercase())

    fun isWithinSize(bytes: Long): Boolean = bytes in 1..MAX_BYTES

    fun check(context: Context, uri: Uri): InputCheck {
        var name = "photo.png"
        var size = -1L
        val resolver = context.contentResolver
        resolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIndex >= 0 && !cursor.isNull(nameIndex)) name = cursor.getString(nameIndex)
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
            }
        }
        val mime = resolver.getType(uri) ?: name.substringAfterLast('.', "").let { extension ->
            when (extension.lowercase()) {
                "png" -> "image/png"
                "jpg", "jpeg" -> "image/jpeg"
                "webp" -> "image/webp"
                else -> ""
            }
        }
        if (!isSupportedMime(mime)) return InputCheck.Rejected(BgRemovalErrors.BAD_IMAGE)
        if (size <= 0) {
            size = runCatching { resolver.openInputStream(uri)?.use { it.available().toLong() } ?: -1L }.getOrDefault(-1L)
        }
        if (size > MAX_BYTES) return InputCheck.Rejected(BgRemovalErrors.TOO_LARGE)
        if (size <= 0) size = 0L
        return InputCheck.Ok(ImageInput(uri, name, mime, size))
    }
}
