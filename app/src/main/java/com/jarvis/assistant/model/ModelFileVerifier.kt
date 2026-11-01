package com.jarvis.assistant.model

import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.CancellationException

/** Never treat a partial file, an HTML response, or mismatched weights as installed. */
object ModelFileVerifier {
    fun verify(file: File, size: Long, sha256: String, cancelled: () -> Boolean = { false }) {
        if (!file.isFile || file.length() != size || size < 1024) {
            throw IOException("Incomplete model: expected $size bytes, found ${file.length()}. Tap Resume.")
        }
        require(sha256.matches(Regex("[a-fA-F0-9]{64}"))) { "The source has no verifiable SHA-256 checksum." }
        file.inputStream().buffered().use { input ->
            val magic = ByteArray(4)
            if (input.read(magic) != 4 || !magic.contentEquals(byteArrayOf(71, 71, 85, 70))) {
                throw IOException("Not a GGUF model. The source returned an invalid file.")
            }
            val digest = MessageDigest.getInstance("SHA-256")
            digest.update(magic)
            val buffer = ByteArray(256 * 1024)
            while (true) {
                if (cancelled()) throw CancellationException("Paused; downloaded bytes are kept.")
                val read = input.read(buffer)
                if (read == -1) break
                digest.update(buffer, 0, read)
            }
            val actual = digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
            if (!actual.equals(sha256, true)) throw IOException("Checksum mismatch. The damaged download must be downloaded again.")
        }
    }
}