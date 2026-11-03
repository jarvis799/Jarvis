package com.jarvis.assistant.voice

import java.util.concurrent.atomic.AtomicInteger

/** Where one utterance should go. Nothing here cancels work already in flight. */
enum class UtteranceRoute {
    LOCAL_COMMAND,
    CONVERSATION,
    CLARIFY,
    IGNORE_AMBIENT,
    ACCEPT_CORRECTION
}

data class Utterance(
    val text: String,
    val fromWake: Boolean = true,
    val speakerVerified: Boolean = true,
    /** Acoustic confidence 0..1 when the recogniser supplies one, otherwise null. */
    val confidence: Float? = null,
    val alternatives: List<String> = emptyList()
)

data class RoutingDecision(
    val route: UtteranceRoute,
    val classification: IntentClassification,
    val reason: String
)

/**
 * The glue between recognition and action: profile + intent + acoustic confidence in, one
 * routing decision out.
 *
 * Rules of the road:
 *  - background speech and ambient noise are dropped without touching a running turn;
 *  - a correction is routed as a correction, never as a brand new unrelated question;
 *  - a risky command with no target asks instead of acting;
 *  - the routing decision itself is stateless with respect to the conversation, so an answer
 *    that is still being generated is never disturbed by a new segment of audio.
 *
 * Thread-safe: recognition callbacks arrive on several threads.
 */
class UtteranceRouter(
    private val profileProvider: () -> ListeningProfile = { ListeningProfile.NORMAL }
) {

    private val decisions = AtomicInteger(0)
    private val ignored = AtomicInteger(0)

    val decisionCount: Int get() = decisions.get()
    val ignoredCount: Int get() = ignored.get()

    fun decide(utterance: Utterance): RoutingDecision {
        decisions.incrementAndGet()
        val profile = profileProvider()
        val text = utterance.text
        val classification = IntentClassifier.classify(text)

        if (classification.category == IntentCategory.AMBIENT_NOISE) {
            return discard(classification, "ambient noise - no request heard")
        }

        if (!utterance.speakerVerified && profile == ListeningProfile.PUBLIC_PLACE) {
            return discard(classification, "speaker is not the verified owner and the profile is Public place")
        }

        if (classification.category == IntentCategory.BACKGROUND_SPEECH) {
            if (profile == ListeningProfile.PUBLIC_PLACE && !utterance.fromWake) {
                return discard(classification, "sounds like television or podcast speech, and no wake word was spoken")
            }
            return RoutingDecision(UtteranceRoute.CLARIFY, classification, "possible background speech - ask before acting")
        }

        if (!utterance.fromWake && !profile.acceptsSpeechWithoutWake &&
            classification.category != IntentCategory.CORRECTION &&
            classification.category != IntentCategory.CONVERSATIONAL_QUERY
        ) {
            return discard(classification, "no wake word in " + profile.label + " mode")
        }

        val acoustic = utterance.confidence
        if (acoustic != null && acoustic < 0.35f) {
            return RoutingDecision(
                UtteranceRoute.CLARIFY, classification,
                "recognition confidence " + acoustic + " is too low to act on"
            )
        }
        if (utterance.alternatives.size >= 2 && classification.confidence < 0.80f) {
            return RoutingDecision(
                UtteranceRoute.CLARIFY, classification,
                "several different transcripts could match - " + utterance.alternatives.take(2).joinToString(" / ")
            )
        }

        return when (classification.category) {
            IntentCategory.CORRECTION ->
                RoutingDecision(UtteranceRoute.ACCEPT_CORRECTION, classification, classification.reason)
            IntentCategory.DEVICE_COMMAND, IntentCategory.MULTI_ACTION_COMMAND ->
                RoutingDecision(UtteranceRoute.LOCAL_COMMAND, classification, classification.reason)
            IntentCategory.CONVERSATIONAL_QUERY, IntentCategory.FOLLOW_UP ->
                RoutingDecision(UtteranceRoute.CONVERSATION, classification, classification.reason)
            IntentCategory.UNCERTAIN_INTENT ->
                if (profile == ListeningProfile.PUBLIC_PLACE && !utterance.fromWake) {
                    discard(classification, "unclear utterance without a wake word in " + profile.label)
                } else {
                    RoutingDecision(UtteranceRoute.CLARIFY, classification, classification.reason)
                }
            IntentCategory.AMBIENT_NOISE, IntentCategory.BACKGROUND_SPEECH ->
                discard(classification, "not addressed to the assistant")
        }
    }

    private fun discard(classification: IntentClassification, reason: String): RoutingDecision {
        ignored.incrementAndGet()
        return RoutingDecision(UtteranceRoute.IGNORE_AMBIENT, classification, reason)
    }
}
