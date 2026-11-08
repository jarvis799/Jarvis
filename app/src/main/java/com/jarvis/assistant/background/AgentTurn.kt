package com.jarvis.assistant.background

import android.content.Context
import com.jarvis.assistant.automation.command.NativeCommandGateway
import com.jarvis.assistant.vision.ScreenAwareness

/**
 * DEV STYLE: the hands run before the words.
 *
 * A plain chat endpoint has no tools, so a request used to come back as an explanation of what
 * could be done instead of the device actually doing it. Every typed or offline turn now gives
 * the local command engine and the screen the first turn, and only asks the model to explain
 * what the device could not do itself.
 */
object AgentTurn {

    private val screenQuestion = Regex(
        "(?i)(where is|where'?s|kothay|kothae|\u0995\u09cb\u09a5\u09be\u09af\u09bc|what'?s on (?:the|my) screen|screen e ki|screen poro)"
    )

    suspend fun run(context: Context, text: String, answer: suspend (String) -> String): String {
        if (NativeCommandGateway.canHandle(context, text)) {
            NativeCommandGateway.execute(context, text)?.let { return it }
        }
        if (screenQuestion.containsMatchIn(text)) {
            return ScreenAwareness.describeLayout(targetFrom(text))
        }
        // Agent 1 plans, Agent 2 reviews, Agent 3 answers - one voice, one final reply.
        val crew = com.jarvis.assistant.agent.crew.MultiAgentCoordinator
        return if (crew.available(context)) crew.run(context, text, answer) else answer(text)
    }

    /** "where is the search box" -> "search box"; an empty target describes the whole screen. */
    private fun targetFrom(text: String): String = text
        .replace(Regex("(?i)^.*?(where is|where'?s|kothay|kothae|\u0995\u09cb\u09a5\u09be\u09af\u09bc)\\s*"), "")
        .replace(Regex("(?i)\\s+(on|in) (the |my )?screen.*$"), "")
        .replace(Regex("(?i)\\s+(ta|ti|\u099f\u09be|\u099f\u09bf)\\s*$"), "")
        .trim()
}
