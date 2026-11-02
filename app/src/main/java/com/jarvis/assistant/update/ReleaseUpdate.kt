package com.jarvis.assistant.update

import android.content.Context
import android.os.Build
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Asks the JARVIS release backend which build is published and whether this one is older. */
object ReleaseUpdate {

    private const val ENDPOINT = "https://jarvis-asistantt.base44.app/functions/publicApi"

    data class Info(
        val versionName: String,
        val versionCode: Int,
        val url: String,
        val notes: String,
        val mandatory: Boolean,
        val checksum: String = "",
        val sizeBytes: Long = 0L
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    fun currentVersionCode(context: Context): Int = try {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        if (Build.VERSION.SDK_INT >= 28) info.longVersionCode.toInt() else @Suppress("DEPRECATION") info.versionCode
    } catch (error: Exception) {
        0
    }

    fun check(context: Context): Info? {
        val payload = JSONObject()
            .put("action", "version")
            .put("version_code", currentVersionCode(context))
        val request = Request.Builder()
            .url(ENDPOINT)
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val text = response.body?.string() ?: return null
            val json = JSONObject(text)
            if (!json.optBoolean("update_available", false)) return null
            val url = json.optString("update_url")
            if (!url.startsWith("https://")) return null
            return Info(
                versionName = json.optString("latest_version"),
                versionCode = json.optInt("latest_version_code", 0),
                url = url,
                notes = json.optString("release_notes"),
                mandatory = json.optBoolean("mandatory", false),
                checksum = json.optString("checksum"),
                sizeBytes = json.optLong("file_size", 0L)
            )
        }
    }
}
