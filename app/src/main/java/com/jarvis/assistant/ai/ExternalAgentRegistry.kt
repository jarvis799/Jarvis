package com.jarvis.assistant.ai

import android.content.Context
import android.content.Intent

/**
 * Registry of installed external AI applications Jarvis can hand a task to.
 *
 * This only discovers and launches an app with the prompt pre-filled. It never drives another
 * app's UI, never asks for credentials and never claims a result it did not see.
 */
object ExternalAgentRegistry {

    enum class Agent { GEMINI, CHATGPT }

    data class Target(val agent: Agent, val packageName: String, val label: String, val installed: Boolean)

    private val packages = mapOf(
        Agent.GEMINI to "com.google.android.apps.bard",
        Agent.CHATGPT to "com.openai.chatgpt"
    )

    private val labels = mapOf(
        Agent.GEMINI to "Gemini",
        Agent.CHATGPT to "ChatGPT"
    )

    fun packageOf(agent: Agent): String = packages.getValue(agent)

    fun available(context: Context): List<Target> = Agent.values().map { agent ->
        val pkg = packageOf(agent)
        Target(agent, pkg, labels.getValue(agent), launchIntent(context, pkg) != null)
    }

    fun installed(context: Context, agent: Agent): Boolean = launchIntent(context, packageOf(agent)) != null

    /**
     * Opens the target app. With a prompt, the app receives it as shared text so the owner only
     * has to send it there - Jarvis does not type into another app.
     */
    fun launch(context: Context, agent: Agent, prompt: String? = null): Boolean {
        val pkg = packageOf(agent)
        if (!prompt.isNullOrBlank()) {
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, prompt)
                setPackage(pkg)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (context.packageManager.resolveActivity(send, 0) != null) {
                context.startActivity(send)
                return true
            }
        }
        val plain = launchIntent(context, pkg) ?: return false
        context.startActivity(plain.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return true
    }

    private fun launchIntent(context: Context, pkg: String): Intent? {
        val pm = context.packageManager
        val intent = pm.getLaunchIntentForPackage(pkg) ?: return null
        return if (intent.resolveActivity(pm) != null) intent else null
    }
}
