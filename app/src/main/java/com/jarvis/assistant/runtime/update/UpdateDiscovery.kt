package com.jarvis.assistant.runtime.update

import com.jarvis.assistant.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.math.min

/** Discovers a signed runtime update manifest, verifies it, and decides
 *  whether it is applicable to this build. The manifest URL is configured at
 *  build time (JARVIS_RUNTIME_MANIFEST_URL). Until it is set, discovery is
 *  disabled and the app reports up-to-date. */
object UpdateDiscovery {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS).build()

    sealed class Result {
        object Disabled : Result()
        object UpToDate : Result()
        data class Available(val manifest: UpdateManifest, val totalSize: Long) : Result()
        data class RequiresAppUpdate(val reason: String) : Result()
        data class Failed(val message: String) : Result()
    }

    fun manifestUrl(): String = BuildConfig.RUNTIME_MANIFEST_URL.trim()

    /** Fetch, parse and verify the manifest with bounded exponential backoff. */
    fun discover(currentRuntimeVersion: String, currentAppVersion: String, deviceSdk: Int): Result {
        val url = manifestUrl()
        if (url.isBlank() || !url.startsWith("https://")) return Result.Disabled
        val raw = runWithBackoff(3) { fetch(url) }
            ?: return Result.Failed("Could not reach the update server.")
        val manifest = try { UpdateManifest.parse(raw) }
        catch (e: RuntimeUpdateException) { return Result.Failed(e.message ?: "Malformed manifest.") }
        if (!ManifestSignatureVerifier.verify(manifest))
            return Result.Failed("Manifest signature did not verify. Update rejected.")
        if (UpdateManifest.compareVersions(manifest.minimumSupportedAppVersion, currentAppVersion) > 0)
            return Result.RequiresAppUpdate("This device's app build is too old for runtime " + manifest.runtimeVersion + ". Update the app first.")
        val compat = manifest.compatibility
        if (compat.sdkMin > 0 && deviceSdk < compat.sdkMin)
            return Result.RequiresAppUpdate("Android SDK " + deviceSdk + " below required " + compat.sdkMin + ".")
        if (compat.minRuntimeVersion.isNotBlank() &&
            UpdateManifest.compareVersions(currentRuntimeVersion, compat.minRuntimeVersion) < 0)
            return Result.RequiresAppUpdate("Runtime " + compat.minRuntimeVersion + " required first.")
        if (UpdateManifest.compareVersions(manifest.runtimeVersion, currentRuntimeVersion) <= 0)
            return Result.UpToDate
        val totalSize = manifest.modules.sumOf { it.size }
        return Result.Available(manifest, totalSize)
    }

    @Throws(IOException::class)
    private fun fetch(url: String): String {
        client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP " + resp.code)
            return resp.body?.string() ?: throw IOException("Empty body")
        }
    }

    private inline fun runWithBackoff(maxAttempts: Int, block: () -> String): String? {
        var lastError: Exception? = null
        for (attempt in 1..maxAttempts) {
            try { return block() }
            catch (e: Exception) {
                lastError = e
                if (attempt < maxAttempts) {
                    val delayMs = min(1000L, 200L * (1L shl (attempt - 1)))
                    try { Thread.sleep(delayMs) } catch (_: InterruptedException) { Thread.currentThread().interrupt() }
                }
            }
        }
        lastError?.printStackTrace()
        return null
    }
}
