package com.jarvis.assistant.model

/** One downloadable GGUF model found in the catalogue. */
data class RemoteModel(
    val id: String,
    val name: String,
    val downloads: Long,
    val fileName: String,
    val sizeBytes: Long,
    val downloadUrl: String,
    val quant: String
)

/** One model already stored on this device. */
data class LocalModel(
    val id: String,
    val name: String,
    val filePath: String,
    val sizeBytes: Long,
    val downloadedAt: Long,
    val quant: String
) {
    fun exists(): Boolean = runCatching { java.io.File(filePath).exists() }.getOrDefault(false)
}

/** Device capability verdict for a model size. */
data class ModelCompat(
    val supported: Boolean,
    val reason: String,
    val ramMb: Int,
    val freeMb: Int
)