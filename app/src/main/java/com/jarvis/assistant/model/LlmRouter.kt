package com.jarvis.assistant.model

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/**
 * DEV STYLE routing rule:
 *  - internet available  -> the configured API answers first
 *  - API unreachable     -> the active on-device model answers, when offline fallback is on
 *  - no internet         -> the on-device model answers directly (when one is installed)
 */
object LlmRouter {

    fun isOnline(context: Context): Boolean {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return true
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    suspend fun answer(context: Context, prompt: String, remote: suspend () -> String): String {
        val store = ModelStore(context)
        val model = store.activeModel()
        val localReady = model != null && model.exists() && LocalModelRuntime.isAvailable()

        if (!isOnline(context)) {
            if (localReady) {
                LocalModelRuntime.generate(model!!.filePath, prompt)?.let { return it }
            }
            throw IllegalStateException("No internet and no working on-device model is installed.")
        }

        return try {
            remote()
        } catch (error: Exception) {
            if (localReady && store.offlineFallback()) {
                val local = LocalModelRuntime.generate(model!!.filePath, prompt)
                if (local != null) return local
            }
            throw error
        }
    }
}