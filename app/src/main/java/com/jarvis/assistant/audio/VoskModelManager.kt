package com.jarvis.assistant.audio

import android.content.Context
import android.content.res.AssetManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.vosk.Model
import org.vosk.SpeakerModel
import java.io.File
import java.io.FileOutputStream

object VoskModelManager {
    private const val TAG = "VoskModelManager"

    private val _isModelReady = MutableStateFlow(false)
    val isModelReady: StateFlow<Boolean> = _isModelReady.asStateFlow()

    private val _isSpeakerModelReady = MutableStateFlow(false)
    val isSpeakerModelReady: StateFlow<Boolean> = _isSpeakerModelReady.asStateFlow()

    @Volatile
    var model: Model? = null
        private set

    @Volatile
    var speakerModel: SpeakerModel? = null
        private set

    @Volatile
    var loadError: String? = null
        private set

    private val isInitializing = java.util.concurrent.atomic.AtomicBoolean(false)

    fun init(context: Context, scope: CoroutineScope) {
        if (model != null && speakerModel != null) return
        if (!isInitializing.compareAndSet(false, true)) return

        loadError = null
        scope.launch(Dispatchers.IO) {
            try {
                // A single copied acoustic file does not prove the whole model was extracted.
                if (model == null) {
                    val targetDir = File(context.filesDir, "vosk-model")
                    val bundleId = context.assets.open("model-en-us/uuid").bufferedReader().use { it.readText().trim() }
                    try {
                        if (!VoskModelCache.isComplete(targetDir, bundleId)) {
                            targetDir.deleteRecursively()
                            check(targetDir.mkdirs()) { "Cannot create wake model storage" }
                            copyAssetFolder(context.assets, "model-en-us", targetDir)
                        }
                        val loadedModel = Model(targetDir.absolutePath)
                        try { VoskModelCache.markComplete(targetDir, bundleId) }
                        catch (e: Exception) { loadedModel.close(); throw e }
                        model = loadedModel
                        _isModelReady.value = true
                        loadError = null
                        Log.i(TAG, "Vosk speech model fully loaded")
                    } catch (e: Exception) {
                        // A broken old cache must never survive every automatic retry.
                        targetDir.deleteRecursively()
                        throw e
                    }
                }

                // 2. Unpack & load speaker recognition model (x-vector extractor)
                try {
                    val spkTargetDir = File(context.filesDir, "vosk-spk-model")
                    val spkMarkerFile = File(spkTargetDir, "final.ext.raw")

                    if (!spkMarkerFile.exists() || spkMarkerFile.length() == 0L) {
                        Log.i(TAG, "Unpacking Vosk Speaker model to: ${spkTargetDir.absolutePath}")
                        spkTargetDir.deleteRecursively()
                        spkTargetDir.mkdirs()
                        copyAssetFolder(context.assets, "model-spk", spkTargetDir)
                        Log.i(TAG, "Vosk Speaker model unpacked successfully.")
                    } else {
                        Log.i(TAG, "Vosk Speaker model already unpacked at: ${spkTargetDir.absolutePath}")
                    }

                    val loadedSpk = SpeakerModel(spkTargetDir.absolutePath)
                    speakerModel = loadedSpk
                    _isSpeakerModelReady.value = true
                    Log.i(TAG, "Vosk SpeakerModel ready in memory!")
                } catch (e: Throwable) {
                    Log.e(TAG, "Failed to load Vosk SpeakerModel: ${e.message}", e)
                }

                isInitializing.set(false)
            } catch (e: Throwable) {
                isInitializing.set(false)
                loadError = e.message ?: e.javaClass.simpleName
                Log.e(TAG, "FATAL: Failed to load Vosk model: ${e.message}", e)
            }
        }
    }

    private fun copyAssetFolder(assetManager: AssetManager, fromAssetPath: String, toDir: File) {
        val list = assetManager.list(fromAssetPath)
        if (list.isNullOrEmpty()) {
            try {
                assetManager.open(fromAssetPath).use { input ->
                    toDir.parentFile?.mkdirs()
                    FileOutputStream(toDir).use { output ->
                        input.copyTo(output)
                    }
                }
            } catch (e: Exception) {
                throw java.io.IOException("Cannot unpack wake asset $fromAssetPath: ${e.message}", e)
            }
        } else {
            toDir.mkdirs()
            for (item in list) {
                val childAsset = if (fromAssetPath.isEmpty()) item else "$fromAssetPath/$item"
                val childTarget = File(toDir, item)
                copyAssetFolder(assetManager, childAsset, childTarget)
            }
        }
    }
}
