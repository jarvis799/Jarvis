package com.jarvis.assistant.automation.flows

import android.content.Context
import com.jarvis.assistant.automation.python.PythonSnippetRunner
import com.jarvis.assistant.vision.ScreenAwareness

/**
 * DEV STYLE: typed and spoken shortcuts that do not need the cloud model.
 * Screen awareness and saved Python snippets must work even when the network is down.
 */
object NativeExtras {

    private val screenPattern = Regex(
        "(?i)^(?:what(?:'s| is) on (?:the |my )?screen|screen e ki ache|screen e ki ase|screen dekho|screen analyze koro|analy[sz]e (?:the |my )screen|screen e ki sob ache)\\??$"
    )

    private val pythonPattern = Regex(
        "(?i)^(?:run |start |chalao |chalau |run koro )?(?:my )?(?:python )?(?:script|snippet|bot) (.+?)(?: chalao| chalau| run koro| run| now)?$"
    )

    fun matches(text: String): Boolean =
        screenPattern.matches(text.trim()) ||
            (pythonPattern.matches(text.trim()) && text.contains("script", true) || text.contains("snippet", true))

    suspend fun route(context: Context, input: String): String? {
        val text = input.trim()
        if (screenPattern.matches(text)) return ScreenAwareness.describe()
        val python = pythonPattern.find(text)?.groupValues?.getOrNull(1)?.trim().orEmpty()
        if (python.isNotBlank() && (text.contains("script", true) || text.contains("snippet", true))) {
            return PythonSnippetRunner.runByName(context, python)
        }
        return null
    }
}