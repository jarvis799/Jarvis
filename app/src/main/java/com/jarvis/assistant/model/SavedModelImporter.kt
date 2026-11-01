package com.jarvis.assistant.model

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.security.MessageDigest

object SavedModelImporter {
    suspend fun read(context: Context, uri: Uri): LocalModel {
        val resolver = context.contentResolver
        val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        } ?: "Imported.gguf"
        val known = RecommendedModels.all.firstOrNull { name.endsWith(it.fileName, true) }
        val directory = File(context.filesDir, "models").apply { mkdirs() }
        val temporary = File.createTempFile("import-", ".part", directory)
        val digest = MessageDigest.getInstance("SHA-256")
        val coroutine = currentCoroutineContext()
        try {
            checkNotNull(resolver.openInputStream(uri)) { "Cannot open the saved model." }.use { input ->
                temporary.outputStream().use { output ->
                    val buffer = ByteArray(256 * 1024)
                    while (true) {
                        coroutine.ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        check(temporary.length() + count <= 8L * 1024 * 1024 * 1024) { "Import limit is 8 GB." }
                        check(directory.usableSpace > count + 64L * 1024 * 1024) { "Free more space for the runtime copy." }
                        output.write(buffer, 0, count); digest.update(buffer, 0, count)
                    }
                }
            }
            val hash = digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
            ModelFileVerifier.verify(temporary, known?.sizeBytes ?: temporary.length(), known?.sha256 ?: hash)
            val destination = File(directory, "import-$hash.gguf")
            if (destination.exists()) temporary.delete() else check(temporary.renameTo(destination)) { "Cannot save runtime copy." }
            return LocalModel(known?.id ?: "imported/$hash", known?.name ?: name, destination.path,
                destination.length(), System.currentTimeMillis(), known?.quant ?: "GGUF · imported, source not authenticated")
        } finally { temporary.delete() }
    }
}