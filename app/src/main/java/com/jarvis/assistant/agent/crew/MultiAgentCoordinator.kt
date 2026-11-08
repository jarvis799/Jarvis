package com.jarvis.assistant.agent.crew

import android.content.Context
import android.util.Log
import com.jarvis.assistant.network.AgentProviderConfig

/**
 * Runs the three-agent exchange and hands back exactly one answer.
 *
 * Agent 1 plans, Agent 2 reviews, Agent 1 revises once if it must, then Agent 3 speaks. The exchange
 * is capped by CrewPolicy and every call has a deadline, so a slow or unavailable provider degrades
 * to the normal single-brain reply instead of stalling the turn.
 */
object MultiAgentCoordinator {

    private const val TAG = "MultiAgentCoordinator"

    fun available(context: Context): Boolean = AgentProviderConfig.enabled(context)

    suspend fun run(context: Context, request: String, fallback: suspend (String) -> String): String {
        if (!AgentProviderConfig.enabled(context)) return fallback(request)
        return try {
            val primary = AgentProviderConfig.providerFor(context, AgentProviderConfig.ROLE_PRIMARY)
            val reviewer = AgentProviderConfig.providerFor(context, AgentProviderConfig.ROLE_REVIEWER)
            val assistant = AgentProviderConfig.providerFor(context, AgentProviderConfig.ROLE_ASSISTANT) ?: primary

            var rounds = 1
            var plan = PrimaryReasoningAgent.plan(context, request, primary, null)
            if (plan.steps.isEmpty()) {
                Log.i(TAG, "no plan produced; answering through the normal path")
                return fallback(request)
            }
            var review = ReviewAgent.review(context, request, plan, reviewer)

            while (CrewPolicy.needsAnotherRound(review, rounds)) {
                rounds += 1
                plan = PrimaryReasoningAgent.plan(context, request, primary, review)
                review = ReviewAgent.review(context, request, plan, reviewer)
            }

            val answer = AssistantAgent.answer(context, request, plan, review, rounds, assistant)
            if (answer.isBlank()) fallback(request) else answer
        } catch (cancel: kotlinx.coroutines.CancellationException) {
            throw cancel
        } catch (error: Exception) {
            Log.w(TAG, "agent exchange failed, using the single-brain reply: " + error.message)
            fallback(request)
        }
    }
}
