package com.jarvis.assistant.background

import android.content.Context
import com.jarvis.assistant.automation.command.NativeCommandGateway
import com.jarvis.assistant.automation.flows.AppFlows
import com.jarvis.assistant.automation.python.PythonSnippetRunner
import com.jarvis.assistant.email.GmailStore
import com.jarvis.assistant.subagent.SubAgentBuilder
import com.jarvis.assistant.vision.ScreenAwareness
import org.json.JSONArray
import org.json.JSONObject

/** Narrow local operations; no claim of success is manufactured from a model response. */
object LiveNativeTools {

    suspend fun execute(context: Context, name: String, args: JSONObject): JSONObject {
        when (name) {
            "website_api" -> {
                val action = args.optString("action")
                val extra = runCatching {
                    JSONObject(args.optString("params").ifBlank { "{}" })
                }.getOrDefault(JSONObject())
                return com.jarvis.assistant.website.WebsiteControl.run(context, action, extra)
            }

            "get_email_status" -> return JSONObject().apply {
                put("credentials_saved", GmailStore.isConnected(context))
                put("connection_verified", GmailStore.connectionVerified(context))
                put("auto_reply_enabled", GmailStore.autoReplyEnabled(context))
                put("last_scan", GmailStore.scanStatus(context))
                put("message", "App Password access is separate from Workspace OAuth. Use run_native_command with read my email, summarize my emails or draft a reply. Do not request new credentials if credentials_saved is true.")
            }

            "screen_awareness" -> {
                val description = ScreenAwareness.describe()
                return JSONObject().apply {
                    put("handled", true)
                    put("verified", ScreenAwareness.isAvailable())
                    put("message", description)
                    put("system_directive", "This is the real current screen, read from accessibility. Answer from it; do not ask the user to start Screen Share for this information.")
                }
            }

            "screen_layout" -> return JSONObject().apply {
                put("handled", true)
                put("verified", ScreenAwareness.isAvailable())
                put("message", ScreenAwareness.describeLayout(args.optString("target")))
                put("system_directive", "This is the real position of that element on the current screen. Answer where it is directly; do not ask the user to describe the screen.")
            }

            "run_python" -> {
                val code = args.optString("code")
                val output = if (code.isBlank()) {
                    "No Python code was provided, so nothing ran."
                } else {
                    PythonSnippetRunner.runInline(code)
                }
                return JSONObject().apply {
                    put("handled", code.isNotBlank())
                    put("verified", true)
                    put("message", output)
                    put("system_directive", "This is the real stdout of python3 inside Jarvis's Alpine Linux. Report what it says; never invent extra results.")
                }
            }

            "run_python_snippet" -> {
                val snippetName = args.optString("name").ifBlank { args.optString("snippet") }
                val output = PythonSnippetRunner.runByName(context, snippetName)
                return JSONObject().apply {
                    put("handled", true)
                    put("verified", true)
                    put("message", output)
                    put("system_directive", "Report the returned output honestly. A missing snippet is a setup step, not a success.")
                }
            }

            "run_app_flow" -> {
                val flow = args.optString("flow")
                val result = AppFlows.run(context, flow, args)
                val steps = JSONArray()
                result.steps.forEach { step ->
                    steps.put(JSONObject().apply {
                        put("step", step.label)
                        put("ok", step.ok)
                        put("detail", step.detail)
                    })
                }
                return JSONObject().apply {
                    put("handled", true)
                    put("verified", result.verified)
                    put("completed", result.ok)
                    put("message", result.message)
                    put("steps", steps)
                    put("system_directive", "Report exactly what the steps show. A tapped button is not a published post; say what is verified and what the user must confirm on screen.")
                }
            }
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