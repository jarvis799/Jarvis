package com.jarvis.assistant.apkagent

import android.content.Context
import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/**
 * DEV STYLE: imports an APK through the Storage Access Framework.
 * The original file on the device is never modified - a verified private
 * reference copy is kept for analysis only.
 */
object ApkImportManager {

    data class ImportedApk(val file: File, val sha256: String, val sizeBytes: Long, val displayName: String?)

    const val MAX_SIZE_BYTES = 2L * 1024 * 1024 * 1024
    const val MIN_SIZE_BYTES = 1024L

    fun importDir(context: Context): File = File(context.filesDir, "apkagent").apply { mkdirs() }

    fun currentImport(context: Context): File? = File(importDir(context), "reference.apk").takeIf { it.exists() }

    /** Copies the picked file into private storage and verifies it. */
    suspend fun import(context: Context, uri: Uri): ImportedApk = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val displayName = queryName(resolver, uri)
        val size = querySize(resolver, uri)
        require(size >= MIN_SIZE_BYTES) { "The selected file is empty or unreadable." }
        require(size <= MAX_SIZE_BYTES) { "That file is larger than 2 GB - not a normal APK." }
        val digest = MessageDigest.getInstance("SHA-256")
        val out = File(importDir(context), "reference.apk")
        out.outputStream().use { fos ->
            val input = resolver.openInputStream(uri) ?: error("Could not open the selected file.")
            input.use { ins ->
                val buffer = ByteArray(1 shl 16)
                var read = ins.read(buffer)
                while (read >= 0) {
                    digest.update(buffer, 0, read)
                    fos.write(buffer, 0, read)
                    read = ins.read(buffer)
                }
            }
        }
        val sha256 = digest.digest().joinToString("") { "%02x".format(it) }
        ImportedApk(out, sha256, size, displayName)
    }

    private fun queryName(resolver: ContentResolver, uri: Uri): String? {
        return runCatching {
            resolver.query(uri, null, null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
            }
        }.getOrNull()
    }

    private fun querySize(resolver: ContentResolver, uri: Uri): Long {
        return runCatching {
            resolver.query(uri, null, null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (idx >= 0 && cursor.moveToFirst()) cursor.getLong(idx) else -1L
            } ?: -1L
        }.getOrDefault(-1L)
    }
}
