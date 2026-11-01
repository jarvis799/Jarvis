package com.jarvis.assistant.model

import android.content.Context
import com.jarvis.assistant.agent.AgentEngine

/** The same native-command and local-inference path used by HTTPS chat; no microphone required. */
object OfflineReply {
    suspend fun answer(context: Context, text: String): String =
        AgentEngine.process(text, "User: $text", "", "", "", context)
}