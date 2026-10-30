package com.jarvis.assistant.model

import android.content.Context

/**
 * DEV STYLE: one honest offline answer path.
 * 1. an installed model answers when the on-device engine is bundled in the build,
 * 2. otherwise the built-in offline answers (time, battery, math, identity) answer,
 * 3. otherwise Jarvis says clearly that this request needs the network again.
 */
object OfflineReply {

    fun answer(context: Context, prompt: String): String {
        val model = ModelStore(context).activeModel()
        if (model != null && model.exists() && LocalModelRuntime.isAvailable()) {
            LocalModelRuntime.generate(model.filePath, prompt)?.takeIf { it.isNotBlank() }?.let { return it.trim() }
        }
        com.jarvis.assistant.util.OfflineEngine.localAnswer(context, prompt)?.let { return it }
        return com.jarvis.assistant.util.OfflineEngine.offlineFallback()
    }

    fun engineLabel(context: Context): String {
        val model = ModelStore(context).activeModel()
        return when {
            model != null && model.exists() && LocalModelRuntime.isAvailable() -> "On-device model: " + model.name
            model != null && model.exists() ->
                "Model installed (" + model.name + ") but the native engine is not bundled in this build, so the built-in offline answers reply."
            else -> "Built-in offline answers. Download a model under Local AI Models for model answers once the native engine is bundled."
        }
    }
}
