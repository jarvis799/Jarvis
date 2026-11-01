package com.jarvis.assistant.model

import android.content.Context

object OfflineModelStatus {
    fun promptDescription(): String = com.jarvis.assistant.JarvisApp.instanceOrNull?.let { describe(it) }
        ?: "Device model status is not available in this session. Do not claim offline readiness."

    fun ready(context: Context): Boolean {
        val store = ModelStore(context)
        val active = store.activeModel() ?: return false
        return LocalModelRuntime.isAvailable() && active.exists() && active.id == store.readyModelId()
    }
    fun describe(context: Context): String {
        if (!LocalModelRuntime.isAvailable()) return LocalModelRuntime.statusMessage
        val store = ModelStore(context)
        val model = store.activeModel()
        return when {
            model == null -> "Engine included · choose a model and auto-setup"
            !model.exists() -> "Model file missing · open downloads to reinstall"
            model.id != store.readyModelId() -> "${model.name} · needs setup verification"
            !store.offlineFallback() -> "${model.name} · tested · offline fallback OFF"
            else -> "${model.name} · ready · offline fallback ON"
        }
    }
}