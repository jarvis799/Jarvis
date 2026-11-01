package com.jarvis.assistant.model

/** The JNI library is compiled into the APK; weights stay in app-private storage. */
object LocalModelRuntime {
    private val loadResult = runCatching { System.loadLibrary("jarvis_llama") }
    fun isAvailable(): Boolean = loadResult.isSuccess
    val statusMessage: String get() = if (isAvailable()) "Offline engine included · CPU inference · no API key"
        else "Offline engine could not load: ${loadResult.exceptionOrNull()?.message ?: "unsupported device"}. Install a build containing the engine for this CPU."

    private external fun generateNative(path: String, system: ByteArray, user: ByteArray, maxTokens: Int): ByteArray
    private external fun warmUpNative(path: String)

    /**
     * Loads the weights into memory ahead of the first question, so an offline reply does not
     * wait for the file read. Called once on a background thread when the app starts.
     */
    fun warmUp(modelPath: String) {
        if (!isAvailable() || !java.io.File(modelPath).isFile) return
        runCatching { warmUpNative(modelPath) }
    }

    /** Called on an IO thread. Serialization keeps simultaneous replies from exhausting RAM. */
    @Synchronized
    fun reply(modelPath: String, system: String, user: String, maxTokens: Int = 128): String {
        check(isAvailable()) { statusMessage }
        check(java.io.File(modelPath).isFile) { "Active model file is missing. Open Settings > Offline AI Models and download it again." }
        require(user.isNotBlank() && user.length <= 7000) { "Use a non-empty message of at most 7000 characters." }
        val bytes = generateNative(modelPath, system.toByteArray(Charsets.UTF_8), user.toByteArray(Charsets.UTF_8), maxTokens.coerceIn(1, 192))
        return String(bytes, Charsets.UTF_8).trim().also {
            check(it.isNotBlank()) { "The local model returned no answer. Try a shorter question or another model." }
        }
    }

    fun generate(modelPath: String, prompt: String, maxTokens: Int = 128): String? =
        if (!isAvailable()) null else reply(modelPath, "You are JARVIS. Be concise and honest; do not invent tool results.", prompt, maxTokens)
}