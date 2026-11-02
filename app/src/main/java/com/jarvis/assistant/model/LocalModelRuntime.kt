package com.jarvis.assistant.model

/** The JNI library is compiled into the APK; weights stay in app-private storage. */
object LocalModelRuntime {
    private val loadResult = runCatching { System.loadLibrary("jarvis_llama") }
    fun isAvailable(): Boolean = loadResult.isSuccess
    val statusMessage: String get() = if (isAvailable()) "Offline engine included · CPU inference · no API key"
        else "Offline engine could not load: ${loadResult.exceptionOrNull()?.message ?: "unsupported device"}. Install a build containing the engine for this CPU."

    private external fun generateNative(path: String, system: ByteArray, user: ByteArray, maxTokens: Int): ByteArray
    private external fun generateStreamNative(path: String, system: ByteArray, user: ByteArray, maxTokens: Int, sink: TokenSink): ByteArray
    private external fun warmUpNative(path: String)
    private external fun releaseNative()

    /** Receives raw UTF-8 bytes from the native engine while the answer is still being written. */
    interface TokenSink {
        fun onBytes(chunk: ByteArray)
    }

    /** Frees the cached weights when Android reports memory pressure. */
    fun release() {
        if (!isAvailable()) return
        runCatching { releaseNative() }
    }

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
    fun reply(modelPath: String, system: String, user: String, maxTokens: Int = 120): String {
        check(isAvailable()) { statusMessage }
        check(java.io.File(modelPath).isFile) { "Active model file is missing. Open Settings > Offline AI Models and download it again." }
        require(user.isNotBlank() && user.length <= 7000) { "Use a non-empty message of at most 7000 characters." }
        // A short cap is what keeps an offline answer snappy on a phone CPU: every extra
        // generated token costs real milliseconds, and long local answers start looping.
        val bytes = generateNative(modelPath, system.toByteArray(Charsets.UTF_8), user.toByteArray(Charsets.UTF_8), maxTokens.coerceIn(1, 160))
        val raw = String(bytes, Charsets.UTF_8).trim()
        val text = com.jarvis.assistant.util.ReplyCleaner.clean(raw)
        return text.also {
            check(it.isNotBlank()) { "The local model returned no answer. Try a shorter question or another model." }
        }
    }

    /**
     * Streaming reply: every generated piece is handed to [onDelta] as plain text while the
     * phone still writes the rest, so the chat bubble (and the spoken answer) starts at once
     * instead of waiting a full minute for the last token.
     *
     * [onDelta] is called from the inference thread - callers must hop to their own dispatcher
     * before touching UI.
     */
    fun replyStream(
        modelPath: String,
        system: String,
        user: String,
        maxTokens: Int = 200,
        onDelta: (String) -> Unit
    ): String {
        check(isAvailable()) { statusMessage }
        check(java.io.File(modelPath).isFile) { "Active model file is missing. Open Settings > Offline AI Models and download it again." }
        require(user.isNotBlank() && user.length <= 7000) { "Use a non-empty message of at most 7000 characters." }

        val received = java.io.ByteArrayOutputStream()
        var shown = 0
        val sink = object : TokenSink {
            override fun onBytes(chunk: ByteArray) {
                received.write(chunk, 0, chunk.size)
                // Decode the whole buffer so a multi-byte Bangla/Hindi glyph split across two
                // chunks can never reach the speaker as a broken character.
                val decoded = String(received.toByteArray(), Charsets.UTF_8)
                var limit = decoded.length
                while (limit > shown && decoded[limit - 1] == '\uFFFD') limit--
                if (limit > shown) {
                    onDelta(decoded.substring(shown, limit))
                    shown = limit
                }
            }
        }

        val bytes = generateStreamNative(
            modelPath,
            system.toByteArray(Charsets.UTF_8),
            user.toByteArray(Charsets.UTF_8),
            maxTokens.coerceIn(1, 160),
            sink
        )
        val text = com.jarvis.assistant.util.ReplyCleaner.clean(String(bytes, Charsets.UTF_8).trim())
        check(text.isNotBlank()) { "The local model returned no answer. Try a shorter question or another model." }
        return text
    }

    fun generate(modelPath: String, prompt: String, maxTokens: Int = 120): String? =
        if (!isAvailable()) null else reply(modelPath, "You are JARVIS. Be concise and honest; do not invent tool results.", prompt, maxTokens)
}