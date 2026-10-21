package com.jarvis.assistant.bgremove

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.coroutineContext

/** Decides where to process, validates the result, and stores the PNG. Never fakes a result. */
class BackgroundRemovalEngine(private val context: Context) {

    private val config = BgRemovalConfig(context)

    fun plan(): RemovalPlan {
        val host = HostRemovalClient.normalizeHost(config.hostUrl)
        if (host != null) {
            val local = host.contains("127.0.0.1") || host.contains("localhost")
            val note = if (local) "Processing on this phone (local host)" else "Processing on your configured host"
            return RemovalPlan(if (local) RemovalMode.LOCAL_DEVICE_SERVER else RemovalMode.CONFIGURED_HOST, HostRemovalClient.endpointFor(host), note)
        }
        val fallback = RemovalPlan(RemovalMode.UNAVAILABLE, "", BgRemovalErrors.HOST_NOT_CONFIGURED)
        return fallback
    }

    fun outputFile(): File {
        val dir = File(context.cacheDir, "bg_removal").apply { if (!exists()) mkdirs() }
        return File(dir, "removed_" + System.currentTimeMillis() + ".png")
    }

    suspend fun remove(input: ImageInput, plan: RemovalPlan): RemovalResult = withContext(Dispatchers.IO) {
        if (plan.mode == RemovalMode.UNAVAILABLE) return@withContext RemovalResult.Failure(plan.note, plan)
        val source = try {
            context.contentResolver.openInputStream(input.uri)?.use { it.readBytes() }
        } catch (error: OutOfMemoryError) {
            return@withContext RemovalResult.Failure(BgRemovalErrors.OUT_OF_MEMORY, plan)
        } ?: return@withContext RemovalResult.Failure(BgRemovalErrors.BAD_IMAGE, plan)
        if (!ImageInputValidator.isWithinSize(source.size.toLong())) {
            return@withContext RemovalResult.Failure(BgRemovalErrors.TOO_LARGE, plan)
        }
        coroutineContext.ensureActive()
        val outcome = HostRemovalClient.remove(plan.endpoint, source, input.displayName, input.mimeType)
        val bytes = outcome.bytes
        if (bytes == null) {
            return@withContext RemovalResult.Failure(outcome.message ?: BgRemovalErrors.HOST_ERROR, plan)
        }
        if (!PngInspector.declaresAlpha(bytes)) {
            return@withContext RemovalResult.Failure(BgRemovalErrors.NO_TRANSPARENCY, plan)
        }
        val verifiable = bytes.size.toLong() <= AlphaCoverage.MAX_VERIFY_BYTES
        val ratio = if (verifiable) {
            try {
                AlphaCoverage.transparentRatio(bytes) ?: 0f
            } catch (error: OutOfMemoryError) {
                return@withContext RemovalResult.Failure(BgRemovalErrors.OUT_OF_MEMORY, plan)
            }
        } else 1f
        if (verifiable && ratio < AlphaCoverage.MIN_RATIO) {
            return@withContext RemovalResult.Failure(BgRemovalErrors.NO_TRANSPARENCY, plan)
        }
        val target = outputFile()
        try {
            target.writeBytes(bytes)
        } catch (error: Exception) {
            return@withContext RemovalResult.Failure(BgRemovalErrors.SAVE_FAILED, plan)
        }
        config.lastPlanNote = plan.note
        RemovalResult.Success(target, plan, ratio, verifiable)
    }

    suspend fun capability(): PythonCapability = LocalPythonProbe.probe()

    fun pingCurrentHost(): RemovalOutcome {
        val host = HostRemovalClient.normalizeHost(config.hostUrl)
            ?: return RemovalOutcome(null, BgRemovalErrors.HOST_NOT_CONFIGURED, false)
        return HostRemovalClient.ping(host)
    }

    /** Throws with a readable reason when the gallery entry cannot be written. */
    fun saveToGallery(png: File, displayName: String): String = ImageSaver.save(context, png, displayName)
}
