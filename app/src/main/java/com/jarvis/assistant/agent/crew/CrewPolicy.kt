package com.jarvis.assistant.agent.crew

/**
 * The rules that keep a three-agent exchange short and finite.
 *
 * Two agents can discuss forever; this file is what makes sure they cannot. The cap is a constant,
 * one extra round is allowed only when the reviewer raised a blocking issue, and the exchange always
 * ends so Agent 3 can answer.
 */
object CrewPolicy {

    const val MAX_ROUNDS = 2
    const val MAX_STEPS = 8

    fun needsAnotherRound(review: CrewReview?, rounds: Int): Boolean {
        if (review == null) return false
        if (rounds >= MAX_ROUNDS) return false
        return !review.approved && review.blocking.isNotEmpty()
    }

    fun conflicts(review: CrewReview?): List<String> =
        if (review == null) emptyList() else review.blocking.filter { it.isNotBlank() }

    fun usableSteps(plan: PlanDraft, review: CrewReview?): List<String> {
        val steps = plan.steps.filter { it.isNotBlank() }.take(MAX_STEPS)
        if (review == null) return steps
        val improved = ArrayList<String>(steps)
        review.improvements.forEach { if (it.isNotBlank() && improved.size < MAX_STEPS) improved.add(it) }
        return improved
    }

    /** The compact brief Agent 3 receives: the approved plan plus anything left open. */
    fun briefForAssistant(plan: PlanDraft, review: CrewReview?, rounds: Int): String {
        val builder = StringBuilder()
        builder.append("GOAL: ").append(plan.goal).append("\n")
        val steps = usableSteps(plan, review)
        if (steps.isEmpty()) {
            builder.append("STEPS: none proposed. Answer from what you already know.\n")
        } else {
            builder.append("STEPS:\n")
            steps.forEachIndexed { index, step -> builder.append(index + 1).append(". ").append(step).append("\n") }
        }
        val open = conflicts(review)
        if (open.isNotEmpty() && rounds >= MAX_ROUNDS) {
            builder.append("UNRESOLVED REVIEW NOTES (mention nothing about agents, be honest if it matters): ")
            builder.append(open.joinToString("; ")).append("\n")
        }
        if (plan.risks.isNotEmpty()) {
            builder.append("RISKS: ").append(plan.risks.take(3).joinToString("; ")).append("\n")
        }
        return builder.toString()
    }
}
