package com.jarvis.assistant.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.CancellationException

class ModelFileVerifierTest {
    private fun sample() = ByteArray(2048) { 7 }.apply {
        this[0] = 71; this[1] = 71; this[2] = 85; this[3] = 70
    }
    private fun digest(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }
    private fun withFile(bytes: ByteArray, block: (File) -> Unit) {
        val file = File.createTempFile("jarvis-model", ".gguf")
        try { file.writeBytes(bytes); block(file) } finally { file.delete() }
    }
    @Test fun acceptsMatchingLengthHeaderAndHash() {
        val bytes = sample()
        withFile(bytes) { ModelFileVerifier.verify(it, bytes.size.toLong(), digest(bytes)) }
    }
    @Test(expected = IOException::class) fun rejectsPartialFile() {
        withFile(sample()) { ModelFileVerifier.verify(it, 4096, digest(sample())) }
    }
    @Test(expected = IOException::class) fun rejectsHtmlInsteadOfWeights() {
        withFile(ByteArray(2048) { 60 }) { ModelFileVerifier.verify(it, 2048, digest(sample())) }
    }
    @Test(expected = IOException::class) fun rejectsSameSizeCorruption() {
        val original = sample()
        val damaged = original.copyOf().apply { this[100] = 9 }
        withFile(damaged) { ModelFileVerifier.verify(it, 2048, digest(original)) }
    }
    @Test(expected = CancellationException::class) fun cancellationNeverPassesVerification() {
        withFile(sample()) { ModelFileVerifier.verify(it, 2048, digest(sample())) { true } }
    }
    @Test fun defaultSourcesHaveImmutableRevisionsAndChecksums() {
        RecommendedModels.all.forEach {
            assertEquals(64, it.sha256.length)
            assertEquals(false, it.downloadUrl.contains("/resolve/main/"))
            assertEquals(true, it.sizeBytes > 600_000_000)
        }
    }
}