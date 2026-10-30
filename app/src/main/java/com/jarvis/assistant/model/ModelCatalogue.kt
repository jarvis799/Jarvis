package com.jarvis.assistant.model

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * DEV STYLE: model catalogue backed by the public Hugging Face API (GGUF filter).
 * No API key needed; only metadata is fetched here, downloads go through ModelDownloader.
 */
object ModelCatalogue {

    private val client = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(40, TimeUnit.SECONDS)
        .build()

    suspend fun search(query: String, limit: Int = 25): List<RemoteModel> = withContext(Dispatchers.IO) {
        val url = buildString {
            append("https://huggingface.co/api/models?filter=gguf&sort=downloads&direction=-1&full=true&limit=")
            append(limit)
            if (query.isNotBlank()) {
                append("&search=")
                append(URLEncoder.encode(query.trim(), "UTF-8"))
            }
        }
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "JarvisAssistant")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Hugging Face returned ${response.code}")
            val body = response.body?.string() ?: "[]"
            parse(body)
        }
    }

    private fun parse(json: String): List<RemoteModel> {
        val array = JSONArray(json)
        val result = ArrayList<RemoteModel>()
        for (index in 0 until array.length()) {
            val entry = array.optJSONObject(index) ?: continue
            val id = entry.optString("id")
            if (id.isBlank()) continue
            val siblings = entry.optJSONArray("siblings") ?: continue
            val file = pickFile(siblings) ?: continue
            val fileName = file.optString("rfilename")
            result.add(
                RemoteModel(
                    id = id,
                    name = id.substringAfterLast('/'),
                    downloads = entry.optLong("downloads"),
                    fileName = fileName,
                    sizeBytes = file.optLong("size", 0L),
                    downloadUrl = "https://huggingface.co/$id/resolve/main/$fileName?download=true",
                    quant = quantOf(fileName)
                )
            )
        }
        return result
    }

    /** Prefers a Q4_K_M quantisation, otherwise the first GGUF file. */
    private fun pickFile(siblings: JSONArray): JSONObject? {
        val files = ArrayList<JSONObject>()
        for (index in 0 until siblings.length()) {
            val file = siblings.optJSONObject(index) ?: continue
            if (file.optString("rfilename").endsWith(".gguf", true)) files.add(file)
        }
        if (files.isEmpty()) return null
        return files.firstOrNull { it.optString("rfilename").contains("Q4_K_M", true) } ?: files.first()
    }

    private fun quantOf(fileName: String): String {
        val match = Regex("(?i)(q\\d(?:_[a-z0-9]+)*)").find(fileName)
        return match?.value?.uppercase(Locale.US) ?: ""
    }
}