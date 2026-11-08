package com.jarvis.assistant.agent.crew

import android.content.Context
import com.jarvis.assistant.network.AiProvider

/**
 * Agent 2 - Independent Review.
 *
 * Reads Agent 1's plan as a critic: missing steps, wrong assumptions, actions that would fail on a
 * real phone. It returns a verdict plus concrete issues, which is what Agent 1 revises against.
 */
object ReviewAgent {

    suspend fun review(
        context: Context,
        request: String,
        plan: PlanDraft,
        provider: AiProvider?
    ): CrewReview? {
        val prompt = buildString {
            append("You are Agent 2, an independent reviewer. You did not write this plan and you are not ")
            append("allowed to be agreeable.\nOwner request: ").append(request).append("\n\n")
            append("Proposed plan:\n")
            plan.steps.forEachIndexed { index, step -> append(index + 1).append(". ").append(step).append("\n") }
            if (plan.risks.isNotEmpty()) append("Stated risks: ").append(plan.risks.joinToString("; ")).append("\n")
            append("\nCheck: does each step actually achieve the goal on Android, is anything missing, is any step impossible, ")
            append("and would the owner hear a wrong confirmation? Be concise.\n")
            append("Reply with JSON only: {\"approved\": boolean, \"blocking\": [string], \"improvements\": [string]}")
        }
        val raw = CrewCall.ask(context, provider, prompt) ?: return null
        val json = CrewCall.json(raw) ?: return null
        val blocking = CrewCall.strings(json.optJSONArray("blocking")).take(4)
        val improvements = CrewCall.strings(json.optJSONArray("improvements")).take(4)
        val approved = json.optBoolean("approved", blocking.isEmpty())
        return CrewReview(approved, blocking, improvements)
    }
}
