package com.jarvis.assistant.automation.status

/** Where an action really is, never where it was hoped to be. */
enum class ActionState { IDLE, LOADING, RUNNING, COMPLETED, FAILED, UNVERIFIED }

data class ActionReport(
    val label: String,
    val host: String,
    val state: ActionState,
    val verified: Boolean,
    val detail: String = ""
) {
    val terminal: Boolean
        get() = state == ActionState.COMPLETED || state == ActionState.FAILED || state == ActionState.UNVERIFIED
}

/**
 * The rules that decide when an action counts as finished.
 *
 * The old code answered a launch with "it may still be loading", so the assistant could repeat
 * loading forever. Here every action reaches a terminal state inside [VERIFY_TIMEOUT_MS], and a
 * success line is only ever produced for a verified action.
 */
object ActionStatusPolicy {

    const val VERIFY_TIMEOUT_MS = 9_000L
    const val POLL_MS = 250L

    fun afterLaunch(launched: Boolean): ActionState =
        if (launched) ActionState.LOADING else ActionState.FAILED

    fun settle(launched: Boolean, verified: Boolean, elapsedMs: Long): ActionState = when {
        !launched -> ActionState.FAILED
        verified -> ActionState.COMPLETED
        elapsedMs >= VERIFY_TIMEOUT_MS -> ActionState.UNVERIFIED
        else -> ActionState.LOADING
    }

    /** The one line Jarvis says out loud. Only a verified action gets a success line. */
    fun spokenLine(label: String, state: ActionState): String = when (state) {
        ActionState.COMPLETED -> "Ji sir, " + label + " open ho gaya."
        ActionState.FAILED -> "Sorry sir, " + label + " open nahi hua."
        ActionState.UNVERIFIED -> "Sir, " + label + " kholne ki koshish ki, par confirm nahi hua."
        else -> ""
    }

    /** What the model is told, so its spoken reply matches the real state. */
    fun toolMessage(label: String, host: String, state: ActionState): String {
        val where = if (host.isBlank()) label else label + " (" + host + ")"
        return when (state) {
            ActionState.COMPLETED ->
                "STATUS: COMPLETED and VERIFIED. " + where + " is on screen in the foreground. Say it is done in one short line. Never say loading, opening or please wait."
            ActionState.FAILED ->
                "STATUS: FAILED. " + where + " did not open. Tell the owner plainly that it failed. Never claim success."
            ActionState.UNVERIFIED ->
                "STATUS: UNVERIFIED after " + (VERIFY_TIMEOUT_MS / 1000L) + " seconds. The launch was requested, but the screen never confirmed it. Do not claim success and do not keep repeating loading - report it as unconfirmed once."
            else -> "STATUS: RUNNING."
        }
    }

    /** "https://gemini.google.com/app" becomes "Gemini", so the spoken line sounds human. */
    fun labelFor(url: String): String {
        val host = hostOf(url)
        if (host.isBlank()) return "website"
        val first = host.substringBefore('.').ifBlank { host }
        return first.replaceFirstChar { it.uppercase() }
    }

    fun hostOf(url: String): String {
        var value = url.trim()
        val scheme = value.indexOf("://")
        if (scheme >= 0) value = value.substring(scheme + 3)
        value = value.substringBefore('/').substringBefore('?').substringBefore('#')
        if (value.startsWith("www.", ignoreCase = true)) value = value.substring(4)
        return value
    }
}
