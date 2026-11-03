package com.jarvis.assistant.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UtteranceRouterTest {

    @Test
    fun simpleCommandTakesTheLocalRoute() {
        val router = UtteranceRouter { ListeningProfile.NORMAL }
        val decision = router.decide(Utterance("open YouTube"))
        assertEquals(UtteranceRoute.LOCAL_COMMAND, decision.route)
    }

    @Test
    fun questionTakesTheConversationRoute() {
        val router = UtteranceRouter { ListeningProfile.NORMAL }
        assertEquals(UtteranceRoute.CONVERSATION, router.decide(Utterance("why do we dream?")).route)
    }

    @Test
    fun publicPlaceIgnoresBareBackgroundSpeech() {
        val router = UtteranceRouter { ListeningProfile.PUBLIC_PLACE }
        val decision = router.decide(Utterance("next up, breaking news", fromWake = false))
        assertEquals(UtteranceRoute.IGNORE_AMBIENT, decision.route)
    }

    @Test
    fun publicPlaceStillAcceptsAWakeWordCommand() {
        val router = UtteranceRouter { ListeningProfile.PUBLIC_PLACE }
        assertEquals(UtteranceRoute.LOCAL_COMMAND, router.decide(Utterance("open YouTube", fromWake = true)).route)
    }

    @Test
    fun publicPlaceDropsUtteranceWithoutWakeWord() {
        val router = UtteranceRouter { ListeningProfile.PUBLIC_PLACE }
        assertEquals(UtteranceRoute.IGNORE_AMBIENT, router.decide(Utterance("open youtube", fromWake = false)).route)
    }

    @Test
    fun unverifiedSpeakerIsIgnoredInPublicPlace() {
        val router = UtteranceRouter { ListeningProfile.PUBLIC_PLACE }
        val decision = router.decide(Utterance("call mom", speakerVerified = false))
        assertEquals(UtteranceRoute.IGNORE_AMBIENT, decision.route)
    }

    @Test
    fun correctionIsNotTreatedAsANewCommand() {
        val router = UtteranceRouter { ListeningProfile.NORMAL }
        assertEquals(UtteranceRoute.ACCEPT_CORRECTION, router.decide(Utterance("no, open Chrome")).route)
    }

    @Test
    fun conflictingTranscriptsAskFirst() {
        val router = UtteranceRouter { ListeningProfile.NORMAL }
        val decision = router.decide(
            Utterance("send it", confidence = 0.6f, alternatives = listOf("send it", "said it", "end it"))
        )
        assertEquals(UtteranceRoute.CLARIFY, decision.route)
    }

    @Test
    fun routingNeverCancelsWork() {
        val router = UtteranceRouter { ListeningProfile.NORMAL }
        router.decide(Utterance("tell me a long story"))
        router.decide(Utterance("open YouTube"))
        assertTrue(router.decisionCount == 2)
        assertTrue(router.ignoredCount == 0)
    }
}
