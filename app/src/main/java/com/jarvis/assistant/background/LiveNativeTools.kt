package com.jarvis.assistant.background

import android.content.Context
import com.jarvis.assistant.automation.command.NativeCommandGateway
import com.jarvis.assistant.email.GmailStore
import com.jarvis.assistant.subagent.SubAgentBuilder
import org.json.JSONObject

/** Narrow local operations; no claim of success is manufactured from a model response. */
object LiveNativeTools {
    suspend fun execute(context: Context, name: String, args: JSONObject): JSONObject {
        if (name == "get_email_status") return JSONObject().apply {
            put("credentials_saved", GmailStore.isConnected(context))
            put("connection_verified", GmailStore.connectionVerified(context))
            put("auto_reply_enabled", GmailStore.autoReplyEnabled(context))
            put("last_scan", GmailStore.scanStatus(context))
            put("message", "App Password access is separate from Workspace OAuth. Use run_native_command with read my email, summarize my emails or draft a reply. Do not request new credentials if credentials_saved is true.")
        }
        val message = if (name == "create_sub_agent") SubAgentBuilder.create(context, args.optString("instruction"))
            else NativeCommandGateway.execute(context, args.optString("command"))
        return JSONObject().apply {
            put("handled", message != null)
            put("verified", false)
            put("message", message ?: "No local command matched. Ask for the missing details; nothing was executed.")
            put("system_directive", "Use only the returned message. A saved rule is not a delivered reply. An alarm request is not a confirmed saved alarm. Report setup blockers, never invent success.")
        }
    }
}