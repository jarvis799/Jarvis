package com.jarvis.assistant.agent.crew

/** Agent 1's plan. Kept small on purpose: the middle agents exchange summaries, not transcripts. */
data class PlanDraft(
    val goal: String,
    val steps: List<String>,
    val risks: List<String>
)

/** Agent 2's independent review of that plan. */
data class CrewReview(
    val approved: Boolean,
    val blocking: List<String>,
    val improvements: List<String>
)

/** What Agent 3 delivers to the owner, after the exchange is closed. */
data class CrewOutcome(
    val answer: String,
    val rounds: Int,
    val reviewed: Boolean,
    val unresolved: List<String>
)
