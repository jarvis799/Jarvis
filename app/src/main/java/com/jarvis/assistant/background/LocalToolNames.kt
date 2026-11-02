package com.jarvis.assistant.background

/**
 * DEV STYLE: the tool names the local engine handles itself.
 * Keeping them in one place lets the Live session dispatch without growing the service file.
 */
object LocalToolNames {
    val ALL: Set<String> = setOf(
        "run_native_command",
        "create_sub_agent",
        "get_email_status",
        "screen_awareness",
        "run_python",
        "run_python_snippet",
        "run_app_flow",
        "website_api"
    )
}