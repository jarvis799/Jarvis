package com.jarvis.assistant.model

/**
 * Bridge to the on-device llama.cpp engine.
 *
 * The engine is a native library named "jarvis_llama" (libjarvis_llama.so). When that library is
 * present in the APK's jniLibs folder it loads here and generate() becomes a real on-device answer.
 * Until then this build reports honestly that on-device inference is unavailable instead of
 * pretending — Jarvis keeps answering through the configured API.
 */
object LocalModelRuntime {

    private val nativeLoaded: Boolean = runCatching { System.loadLibrary("jarvis_llama") }.isSuccess

    fun isAvailable(): Boolean = nativeLoaded

    val statusMessage: String
        get() = if (nativeLoaded) {
            "On-device engine loaded. The active model answers without internet."
        } else {
            "On-device engine is not bundled in this build yet. Models can be downloaded and kept on the phone, and Jarvis answers through your API until the native engine is added."
        }

    /**
     * Native entry point expected from libjarvis_llama.so:
     *   Java_com_jarvis_assistant_model_LocalModelRuntime_generateNative(path, prompt, maxTokens)
     */
    private external fun generateNative(modelPath: String, prompt: String, maxTokens: Int): String

    fun generate(modelPath: String, prompt: String, maxTokens: Int = 256): String? {
        if (!nativeLoaded) return null
        return runCatching { generateNative(modelPath, prompt, maxTokens) }.getOrNull()
    }
}