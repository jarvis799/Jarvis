package com.jarvis.assistant.automation.command

import org.junit.Assert.assertTrue
import org.junit.Test

class CommandParserMediaTest {

    private fun isPause(text: String) =
        CommandParser.parse(text).command is JarvisCommand.PauseMedia

    private fun isStop(text: String) =
        CommandParser.parse(text).command is JarvisCommand.StopMedia

    @Test
    fun banglishStopSongParsesAsStop() {
        assertTrue(isStop("gaan band koro"))
    }

    @Test
    fun hindiStopMusicParsesAsStop() {
        assertTrue(isStop("music band karo"))
    }

    @Test
    fun banglaStopMusicParsesAsStop() {
        assertTrue(isStop("গান বন্ধ করো"))
    }

    @Test
    fun englishStopMusicParsesAsStop() {
        assertTrue(isStop("stop the music"))
    }

    @Test
    fun pauseParsesAsPause() {
        assertTrue(isPause("pause music"))
        assertTrue(isPause("pause the video"))
    }

    @Test
    fun resumeStillParses() {
        val result = CommandParser.parse("resume music")
        assertTrue(result.command is JarvisCommand.ResumeMedia)
    }
}
