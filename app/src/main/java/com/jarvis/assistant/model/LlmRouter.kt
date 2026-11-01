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

    suspend fun localAnswer(context: Context, input: String, system: String = LocalJarvisPrompt.system(context)): String {
        val store = ModelStore(context)
        check(store.offlineFallback()) { "Offline fallback is off. Enable it in Settings > Offline AI Models." }
        val model = store.activeModel()
            ?: throw IllegalStateException("No local model is ready. Open Settings > Offline AI Models and use Download & auto-setup.")
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            LocalModelRuntime.reply(model.filePath, system, input)
        }
    }

    suspend fun answer(context: Context, prompt: String, remote: suspend () -> String): String =
        answer(context, prompt, LocalJarvisPrompt.system(context), remote)

    suspend fun answer(context: Context, input: String, system: String, remote: suspend () -> String): String {
        if (!isOnline(context)) return localAnswer(context, input, system)
        return try { remote() }
        catch (error: kotlinx.coroutines.CancellationException) { throw error }
        catch (error: java.io.IOException) {
            val store = ModelStore(context)
            if (store.offlineFallback() && store.activeModel()?.exists() == true) localAnswer(context, input, system)
            else throw error
        }
    }
}