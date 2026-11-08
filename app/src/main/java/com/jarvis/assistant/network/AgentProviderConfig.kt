package com.jarvis.assistant.network

import android.content.Context

/**
 * Which brain each agent role uses.
 *
 * Agent 1 (reasoning) and Agent 2 (review) are deliberately allowed to run on different providers:
 * a review is worth much less when the same model checks its own work. Agent 3 answers the owner and
 * follows the primary provider by default. Provider ids point at entries the owner saved in
 * AgentProviderStore, so keys stay in the app's existing on-device provider store and are never
 * written into code.
 */
object AgentProviderConfig {

    private const val PREFS = "jarvis_agent_crew"

    const val ROLE_PRIMARY = "primary"
    const val ROLE_REVIEWER = "reviewer"
    const val ROLE_ASSISTANT = "assistant"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun enabled(context: Context): Boolean = prefs(context).getBoolean("enabled", true)

    fun setEnabled(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean("enabled", on).apply()
    }

    fun providerIdFor(context: Context, role: String): String =
        prefs(context).getString("provider_" + role, "").orEmpty()

    fun setProviderId(context: Context, role: String, providerId: String) {
        prefs(context).edit().putString("provider_" + role, providerId).apply()
    }

    /**
     * Resolves a role to a real provider. An explicit choice wins when it still exists; otherwise the
     * reviewer prefers a Gemini entry (a different brain from the primary when one is configured) and
     * every other role takes the first enabled provider.
     */
    fun providerFor(context: Context, role: String): AiProvider? {
        val all = AiProviderStore.loadAll(context).filter { it.enabled }
        if (all.isEmpty()) return null
        val chosen = providerIdFor(context, role)
        if (chosen.isNotBlank()) {
            all.firstOrNull { it.id == chosen }?.let { return it }
        }
        if (role == ROLE_REVIEWER) {
            val gemini = all.firstOrNull { it.kind == AiProvider.KIND_GEMINI }
            if (gemini != null) return gemini
        }
        return all.first()
    }
}
