package com.jarvis.assistant.voice

import org.junit.Assert.*
import org.junit.Test

class WakePhraseMatcherTest {
    @Test fun decoderGrammarIncludesRealJervisPronunciation() {
        val phrases = WakePhraseMatcher.recognitionPhrases(setOf("jarvis", "hey jarvis"))
        assertTrue(phrases.containsAll(setOf("jarvis", "hey jarvis", "jervis", "hey jervis")))
    }
    @Test fun renamedAssistantDoesNotGainJarvisAliases() {
        assertEquals(setOf("nova", "hey nova"), WakePhraseMatcher.recognitionPhrases(setOf("nova", "hey nova")))
    }
    @Test fun substringIsNotRewritten() {
        assertEquals(setOf("jarvison"), WakePhraseMatcher.recognitionPhrases(setOf("jarvison")))
    }
    @Test fun everyAddedAliasCanActuallyTriggerWake() {
        val wake = setOf("jarvis", "hey jarvis")
        WakePhraseMatcher.recognitionPhrases(wake).forEach {
            assertTrue(WakePhraseMatcher.containsWakeWord(it.split(" "), wake))
        }
    }
}
