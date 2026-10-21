package com.jarvis.assistant.bgremove

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

data class RemovalOutcome(val bytes: ByteArray?, val message: String?, val reachable: Boolean)

/** Talks to the documented POST /remove-bg endpoint. No mock output, no assumed server. */
object HostRemovalClient {

    const val REMOVE_PATH = "/remove-bg"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    /** Loopback may be plain http; anything leaving the phone must be https. Null means unusable. */
    fun normalizeHost(raw: String): String? {
        val trimmed = raw.trim().trimEnd('/')
        if (trimmed.isEmpty()) return null
        val withScheme = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed else "http://" + trimmed
        val loopback = withScheme.startsWith("http://127.0.0.1") || withScheme.startsWith("http://localhost")
        if (withScheme.startsWith("http://") && !loopback) return null
        return withScheme
    }

    fun isPlainHttpHost(raw: String): Boolean {
        val trimmed = raw.trim()
        if (!trimmed.startsWith("http://")) return false
        return !(trimmed.startsWith("http://127.0.0.1") || trimmed.startsWith("http://localhost"))
    }

    fun baseOf(host: String): String = host.trimEnd('/').removeSuffix(REMOVE_PATH).trimEnd('/')

    fun endpointFor(host: String): String = baseOf(host) + REMOVE_PATH

    fun interpret(status: Int, contentType: String?, bytes: ByteArray): RemovalOutcome = when {
        status == 200 && contentType != null && contentType.lowercase().contains("image/png") && PngInspector.isPng(bytes) ->
            RemovalOutcome(bytes, null, true)
        status == 200 -> RemovalOutcome(null, BgRemovalErrors.BAD_RESPONSE, true)
        status == 413 -> RemovalOutcome(null, "The host says that image is too large.", true)
        status == 415 -> RemovalOutcome(null, BgRemovalErrors.BAD_IMAGE, true)
        status == 404 -> RemovalOutcome(null, "The host answered but has no " + REMOVE_PATH + " endpoint.", true)
        else -> RemovalOutcome(null, BgRemovalErrors.HOST_ERROR + " (HTTP " + status + ")", true)
    }

    fun remove(endpoint: String, imageBytes: ByteArray, fileName: String, mimeType: String): RemovalOutcome {
        val body = imageBytes.toRequestBody(mimeType.toMediaType())
        val multipart = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", fileName, body)
            .build()
        val request = Request.Builder().url(endpoint).post(multipart).build()
        return try {
            client.newCall(request).execute().use { response ->
                interpret(response.code, response.header("Content-Type"), response.body?.bytes() ?: ByteArray(0))
            }
        } catch (io: IOException) {
            RemovalOutcome(null, BgRemovalErrors.HOST_UNREACHABLE + " (" + (io.message ?: "network error") + ")", false)
        }
    }

    /** Any HTTP answer means something is listening; only a socket failure means unreachable. */
    fun ping(host: String): RemovalOutcome {
        val base = baseOf(host)
        val request = Request.Builder().url(base).get().build()
        return try {
            client.newCall(request).execute().use { response ->
                RemovalOutcome(null, "Host answered with HTTP " + response.code + ".", true)
            }
        } catch (io: IOException) {
            RemovalOutcome(null, BgRemovalErrors.HOST_UNREACHABLE + " (" + (io.message ?: "network error") + ")", false)
        }
    }
}
