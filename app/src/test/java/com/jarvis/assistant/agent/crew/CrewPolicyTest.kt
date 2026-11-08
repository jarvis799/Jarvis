package com.jarvis.assistant.agent.crew

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrewPolicyTest {

    private val plan = PlanDraft("open Gemini", listOf("find the app", "open it"), emptyList())

    @Test
    fun anApprovedPlanEndsTheExchange() {
        val review = CrewReview(approved = true, blocking = emptyList(), improvements = listOf("also confirm the page"))
        assertFalse(CrewPolicy.needsAnotherRound(review, 1))
    }

    @Test
    fun aBlockingIssueAllowsExactlyOneMoreRound() {
        val review = CrewReview(approved = false, blocking = listOf("step 2 cannot work offline"), improvements = emptyList())
        assertTrue(CrewPolicy.needsAnotherRound(review, 1))
        assertFalse(CrewPolicy.needsAnotherRound(review, CrewPolicy.MAX_ROUNDS))
    }

    @Test
    fun aMissingReviewNeverStartsADiscussion() {
        assertFalse(CrewPolicy.needsAnotherRound(null, 1))
    }

    @Test
    fun theBriefIsBoundedAndKeepsTheStepsInOrder() {
        val many = PlanDraft("goal", (1..20).map { "step " + it }, emptyList())
        val brief = CrewPolicy.briefForAssistant(many, null, 1)
        assertTrue(brief.startsWith("GOAL: goal"))
        assertTrue(brief.contains("1. step 1"))
        assertTrue(brief.contains("8. step 8"))
        assertFalse(brief.contains("step 9"))
    }

    @Test
    fun unresolvedNotesAreCarriedIntoTheFinalBrief() {
        val review = CrewReview(approved = false, blocking = listOf("unverified claim"), improvements = emptyList())
        val brief = CrewPolicy.briefForAssistant(plan, review, CrewPolicy.MAX_ROUNDS)
        assertTrue(brief.contains("UNRESOLVED REVIEW NOTES"))
        assertTrue(brief.contains("unverified claim"))
    }

    @Test
    fun improvementsAreFoldedIntoThePlanWithoutBreakingTheCap() {
        val review = CrewReview(false, emptyList(), listOf("verify the screen"))
        assertEquals(listOf("find the app", "open it", "verify the screen"), CrewPolicy.usableSteps(plan, review))
    }
}
