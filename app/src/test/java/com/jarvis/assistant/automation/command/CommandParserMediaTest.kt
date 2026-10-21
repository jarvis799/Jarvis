package com.jarvis.assistant.automation.command

import org.junit.Assert.assertTrue
import org.junit.Test

class CommandParserMediaTest {

    private fun isPause(text: String) =
        CommandParser.parse(text).command is JarvisCommand.PauseMedia

    @Test
    fun banglishStopSongParsesAsPause() {
        assertTrue(isPause("gaan band koro"))
    }

    @Test
    fun hindiStopMusicParsesAsPause() {
        assertTrue(isPause("music band karo"))
    }

    @Test
    fun banglaStopMusicParsesAsPause() {
        assertTrue(isPause("গান বন্ধ করো"))
    }

    @Test
    fun englishStopMusicParsesAsPause() {
        assertTrue(isPause("stop the music"))
    }

    @Test
    fun resumeStillParses() {
        val result = CommandParser.parse("resume music")
        assertTrue(result.command is JarvisCommand.ResumeMedia)
    }
}
