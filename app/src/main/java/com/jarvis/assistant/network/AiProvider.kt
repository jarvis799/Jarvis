package com.jarvis.assistant.network

import android.content.Context
import com.jarvis.assistant.JarvisApp
import org.json.JSONArray
import org.json.JSONObject

/** One AI brain the assistant may use: a Gemini key or any custom HTTPS chat endpoint. */
data class AiProvider(
    val id: String,
    val label: String,
    val kind: String,
    val key: String = "",
    val endpoint: String = "",
    val model: String = "",
    val enabled: Boolean = true
) {
    companion object {
        const val KIND_GEMINI = "gemini"
        const val KIND_CUSTOM = "custom"
    }

    fun summary(): String =
        if (kind == KIND_GEMINI) "Gemini - " + model.ifBlank { "gemini-2.5-flash" }
        else endpoint.substringAfter("://").take(52)
}

/**
 * Ordered provider list. The first entry answers; when its quota is gone, its key is dead or
 * it times out, the next entry takes the turn. Add as many entries as the owner likes.
 */
object AiProviderStore {

    fun loadAll(context: Context): List<AiProvider> {
        val raw = JarvisApp.instance.preferences.aiProvidersJson
        if (raw.isBlank()) return seed(context)
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index -> array.optJSONObject(index)?.let { decode(it) } }
        }.getOrDefault(emptyList())
    }

    fun load(context: Context): List<AiProvider> = loadAll(context).filter { it.enabled && usable(it) }

    private fun usable(provider: AiProvider): Boolean =
        if (provider.kind == AiProvider.KIND_GEMINI) provider.key.isNotBlank()
        else provider.endpoint.startsWith("https://", true)

    fun save(context: Context, providers: List<AiProvider>): Boolean {
        val array = JSONArray()
        providers.forEach { array.put(encode(it)) }
        return runCatching {
            JarvisApp.instance.preferences.aiProvidersJson = array.toString()
            true
        }.getOrDefault(false)
    }

    fun add(context: Context, provider: AiProvider): List<AiProvider> {
        val next = loadAll(context) + provider
        save(context, next)
        return next
    }

    fun remove(context: Context, id: String): List<AiProvider> {
        val next = loadAll(context).filter { it.id != id }
        save(context, next)
        return next
    }

    /** Older installs kept one Gemini key and one endpoint; those become the first entries. */
    private fun seed(context: Context): List<AiProvider> {
        val prefs = JarvisApp.instance.preferences
        val seeded = mutableListOf<AiProvider>()
        prefs.apiKeys.forEachIndexed { index, key ->
            seeded.add(
                AiProvider(
                    id = "gemini-" + (index + 1),
                    label = if (index == 0) "Gemini (main)" else "Gemini " + (index + 1),
                    kind = AiProvider.KIND_GEMINI,
                    key = key,
                    model = prefs.aiModel
                )
            )
        }
        val endpoint = prefs.customEndpoint.trim()
        if (endpoint.isNotBlank()) {
            seeded.add(
                AiProvider(
                    id = "custom-1",
                    label = "My endpoint",
                    kind = AiProvider.KIND_CUSTOM,
                    key = prefs.apiKey,
                    endpoint = endpoint,
                    model = prefs.customChatModel
                )
            )
        }
        if (seeded.isNotEmpty()) save(context, seeded)
        return seeded
    }

    private fun encode(provider: AiProvider): JSONObject = JSONObject()
        .put("id", provider.id)
        .put("label", provider.label)
        .put("kind", provider.kind)
        .put("key", provider.key)
        .put("endpoint", provider.endpoint)
        .put("model", provider.model)
        .put("enabled", provider.enabled)

    private fun decode(json: JSONObject): AiProvider = AiProvider(
        id = json.optString("id"),
        label = json.optString("label"),
        kind = json.optString("kind", AiProvider.KIND_GEMINI),
        key = json.optString("key"),
        endpoint = json.optString("endpoint"),
        model = json.optString("model"),
        enabled = json.optBoolean("enabled", true)
    )
}
