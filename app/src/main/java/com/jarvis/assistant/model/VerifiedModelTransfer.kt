package com.jarvis.assistant.model

import android.content.Context
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeUnit

/** One transfer instance per install. Partial bytes survive cancellation or process death. */
class VerifiedModelTransfer(context: Context) {
    private val directory = File(context.applicationContext.filesDir, "models").apply { mkdirs() }
    private val client = OkHttpClient.Builder().connectTimeout(30, TimeUnit.SECONDS).readTimeout(45, TimeUnit.SECONDS).build()
    @Volatile private var cancelled = false
    @Volatile private var call: Call? = null
    fun cancel() { cancelled = true; call?.cancel() }
    private fun checkCancelled() { if (cancelled) throw CancellationException("Paused. Tap Resume to continue.") }
    private fun target(model: RemoteModel) = File(directory,
        (model.id + "-" + model.fileName).replace(Regex("[^A-Za-z0-9._-]"), "_").take(180))

    fun download(model: RemoteModel, progress: (Long, Long) -> Unit, stage: (String) -> Unit): File {
        require(model.sizeBytes > 0 && model.sha256.length == 64) { "Verified source metadata is required before downloading." }
        val destination = target(model)
        val partial = File(destination.path + ".part")
        if (destination.isFile) {
            stage("Checking saved model…")
            try { ModelFileVerifier.verify(destination, model.sizeBytes, model.sha256) { cancelled } }
            catch (error: IOException) {
                destination.delete()
                throw IOException("Saved model was damaged and removed. Tap Retry to download a clean copy.", error)
            }
            return destination
        }
        if (partial.length() > model.sizeBytes) partial.delete()
        if (partial.length() < model.sizeBytes) {
            checkCancelled()
            if (directory.usableSpace < model.sizeBytes - partial.length() + 64L * 1024 * 1024) {
                throw IOException("Not enough storage. Free space and tap Resume; downloaded bytes are kept.")
            }
            stage("Downloading…")
            val existing = partial.length()
            val request = Request.Builder().url(model.downloadUrl).header("Accept-Encoding", "identity")
                .header("User-Agent", "JarvisAssistant/2.0.7")
            if (existing > 0) request.header("Range", "bytes=$existing-")
            val active = client.newCall(request.build())
            call = active
            checkCancelled()
            active.execute().use { response ->
                if (!response.isSuccessful) throw IOException("Model download failed: HTTP ${response.code}. Check connection and tap Resume.")
                val append = response.code == 206 && existing > 0
                if (response.code == 206 && response.header("Content-Range")?.startsWith("bytes $existing-") != true) {
                    throw IOException("Source returned an incorrect resume range. Retry the download.")
                }
                val body = response.body ?: throw IOException("Empty model response.")
                var done = if (append) existing else 0L
                val length = body.contentLength()
                if (length > 0 && done + length != model.sizeBytes) throw IOException("Source file size changed; download was not accepted.")
                var reported = done
                progress(done, model.sizeBytes)
                FileOutputStream(partial, append).use { output ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(128 * 1024)
                        while (true) {
                            checkCancelled()
                            val count = input.read(buffer)
                            if (count == -1) break
                            done += count
                            if (done > model.sizeBytes) throw IOException("Source sent more bytes than expected.")
                            output.write(buffer, 0, count)
                            if (done - reported >= 1024 * 1024 || done == model.sizeBytes) {
                                progress(done, model.sizeBytes); reported = done
                            }
                        }
                    }
                    output.fd.sync()
                }
            }
            call = null
        }
        checkCancelled()
        stage("Verifying GGUF and SHA-256…")
        try { ModelFileVerifier.verify(partial, model.sizeBytes, model.sha256) { cancelled } }
        catch (error: IOException) {
            if (partial.length() == model.sizeBytes) partial.delete()
            throw error
        }
        checkCancelled()
        if (!partial.renameTo(destination)) throw IOException("Verified model could not be saved. Free storage and retry.")
        return destination
    }
}