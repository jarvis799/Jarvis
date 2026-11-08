package com.jarvis.assistant.agent.crew

import android.content.Context
import com.jarvis.assistant.network.AiProvider

/**
 * Agent 1 - Primary Reasoning.
 *
 * Understands the request, analyses it and writes a short executable plan. It never speaks to the
 * owner; its output is a plan for Agent 2 to review.
 */
object PrimaryReasoningAgent {

    suspend fun plan(
        context: Context,
        request: String,
        provider: AiProvider?,
        feedback: CrewReview?
    ): PlanDraft {
        val prompt = buildString {
            append("You are Agent 1, the primary reasoning engine of a phone assistant called JARVIS.\n")
            append("Owner request: ").append(request).append("\n\n")
            append("Produce an execution plan an Android assistant can follow on this device.\n")
            append("Rules: use only steps the phone can really do (open apps, websites, send intents, answer in text). ")
            append("Never invent capabilities. Keep it under 8 steps. Name real risks you see.\n")
            if (feedback != null && feedback.blocking.isNotEmpty()) {
                append("\nThe independent reviewer rejected your previous plan with these blocking issues:\n")
                feedback.blocking.forEach { append("- ").append(it).append("\n") }
                append("Return a corrected plan that resolves every one of them.\n")
            }
            append("\nReply with JSON only: {\"goal\": string, \"steps\": [string], \"risks\": [string]}")
        }
        val raw = CrewCall.ask(context, provider, prompt) ?: return PlanDraft(request, emptyList(), emptyList())
        val json = CrewCall.json(raw) ?: return PlanDraft(request, listOf(raw.trim()), emptyList())
        val steps = CrewCall.strings(json.optJSONArray("steps")).take(CrewPolicy.MAX_STEPS)
        val risks = CrewCall.strings(json.optJSONArray("risks")).take(4)
        val goal = json.optString("goal", request).ifBlank { request }
        return PlanDraft(goal, steps, risks)
    }
}
