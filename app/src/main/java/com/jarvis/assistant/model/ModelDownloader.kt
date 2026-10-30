package com.jarvis.assistant.model

import android.content.Context
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * Resumable GGUF downloader: pause keeps the partial file, resume continues with an HTTP Range request.
 * Files land in filesDir/models and are private to Jarvis.
 */
class ModelDownloader(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    @Volatile private var running = false
    @Volatile private var paused = false
    private var activeCall: Call? = null

    fun isRunning(): Boolean = running

    fun pause() {
        paused = true
        running = false
        runCatching { activeCall?.cancel() }
        activeCall = null
    }

    fun start(
        model: RemoteModel,
        onProgress: (downloaded: Long, total: Long) -> Unit,
        onDone: (File?) -> Unit,
        onError: (String) -> Unit
    ) {
        if (running) {
            onError("A download is already running.")
            return
        }
        running = true
        paused = false

        val directory = File(context.filesDir, "models").apply { mkdirs() }
        val target = File(directory, safeName(model))

        Thread {
            try {
                var existing = if (target.exists()) target.length() else 0L
                val builder = Request.Builder()
                    .url(model.downloadUrl)
                    .header("User-Agent", "JarvisAssistant")
                if (existing > 0) builder.header("Range", "bytes=$existing-")
                val call = client.newCall(builder.build())
                activeCall = call

                call.execute().use { response ->
                    if (!response.isSuccessful) {
                        if (running) {
                            running = false
                            onError("Server returned ${response.code}.")
                        }
                        return@use
                    }
                    val resuming = response.code == 206 && existing > 0
                    if (!resuming && existing > 0) {
                        target.delete()
                        existing = 0L
                    }
                    val body = response.body ?: throw java.io.IOException("Empty response body")
                    val total = if (resuming) existing + body.contentLength() else body.contentLength()
                    var reported = 0L
                    FileOutputStream(target, resuming).use { output ->
                        body.byteStream().use { input ->
                            val buffer = ByteArray(64 * 1024)
                            var done = existing
                            while (true) {
                                val read = input.read(buffer)
                                if (read <= 0) break
                                output.write(buffer, 0, read)
                                done += read
                                if (done - reported >= 1_000_000L || (total > 0 && done >= total)) {
                                    reported = done
                                    onProgress(done, total)
                                }
                            }
                        }
                    }
                }
                activeCall = null
                if (running) {
                    running = false
                    if (target.exists() && target.length() > 0) onDone(target)
                    else onError("Download finished with an empty file.")
                }
            } catch (error: Exception) {
                activeCall = null
                if (running) {
                    running = false
                    onError(error.message ?: "Download failed.")
                }
            }
        }.start()
    }

    private fun safeName(model: RemoteModel): String {
        val raw = "${model.id}-${model.fileName}"
        return raw.replace(Regex("[^A-Za-z0-9._-]"), "_").take(120) + ".gguf"
    }
}