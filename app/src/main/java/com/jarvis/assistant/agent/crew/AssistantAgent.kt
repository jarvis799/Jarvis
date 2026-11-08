package com.jarvis.assistant.agent.crew

import android.content.Context
import com.jarvis.assistant.network.AiProvider

/**
 * Agent 3 - the only user-facing assistant.
 *
 * Takes the exchanged, reviewed result and delivers one clear answer in JARVIS's voice. It never
 * mentions agents, planning or reviewing; the owner should feel they simply asked Jarvis.
 */
object AssistantAgent {

    suspend fun answer(
        context: Context,
        request: String,
        plan: PlanDraft,
        review: CrewReview?,
        rounds: Int,
        provider: AiProvider?
    ): String {
        val brief = CrewPolicy.briefForAssistant(plan, review, rounds)
        val prompt = buildString {
            append("You are JARVIS, a calm, capable Android assistant speaking to your owner.\n")
            append("Owner said: ").append(request).append("\n\n")
            append("Your team already worked this out. Use it, do not describe it:\n")
            append(brief)
            append("\nAnswer in the owner's own language (Bengali, Hindi or English - match the request). ")
            append("Be short and direct. Never mention agents, plans, reviews or rounds. ")
            append("If something could not be verified, say so plainly instead of claiming it worked. ")
            append("Do not say loading or please wait.")
        }
        val raw = CrewCall.ask(context, provider, prompt)
        return raw?.trim().orEmpty()
    }
}
