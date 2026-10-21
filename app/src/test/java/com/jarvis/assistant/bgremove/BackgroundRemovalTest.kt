package com.jarvis.assistant.bgremove

import com.jarvis.assistant.automation.command.CommandParser
import com.jarvis.assistant.automation.command.JarvisCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundRemovalTest {

    private fun pngWithColourType(colourType: Int, extra: String = ""): ByteArray {
        val bytes = ArrayList<Byte>()
        listOf(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A).forEach { bytes.add(it.toByte()) }
        listOf(0, 0, 0, 13).forEach { bytes.add(it.toByte()) }
        "IHDR".toByteArray(Charsets.ISO_8859_1).forEach { bytes.add(it) }
        listOf(0, 0, 0, 4, 0, 0, 0, 4, 8, colourType, 0, 0, 0).forEach { bytes.add(it.toByte()) }
        extra.toByteArray(Charsets.ISO_8859_1).forEach { bytes.add(it) }
        repeat(8) { bytes.add(0) }
        return bytes.toByteArray()
    }

    @Test
    fun acceptsOnlyRealImageTypes() {
        assertTrue(ImageInputValidator.isSupportedMime("image/png"))
        assertTrue(ImageInputValidator.isSupportedMime("IMAGE/JPEG"))
        assertTrue(ImageInputValidator.isSupportedMime("image/webp"))
        assertFalse(ImageInputValidator.isSupportedMime("application/pdf"))
        assertFalse(ImageInputValidator.isSupportedMime("image/gif"))
        assertFalse(ImageInputValidator.isSupportedMime(null))
    }

    @Test
    fun enforcesTheSizeLimit() {
        assertTrue(ImageInputValidator.isWithinSize(2048))
        assertTrue(ImageInputValidator.isWithinSize(ImageInputValidator.MAX_BYTES))
        assertFalse(ImageInputValidator.isWithinSize(0))
        assertFalse(ImageInputValidator.isWithinSize(ImageInputValidator.MAX_BYTES + 1))
    }

    @Test
    fun detectsRealPngFiles() {
        assertTrue(PngInspector.isPng(pngWithColourType(6)))
        assertTrue(PngInspector.declaresAlpha(pngWithColourType(6)))
        assertFalse(PngInspector.declaresAlpha(pngWithColourType(2)))
        assertTrue(PngInspector.declaresAlpha(pngWithColourType(2, "tRNS")))
        assertFalse(PngInspector.isPng(ByteArray(40)))
        assertFalse(PngInspector.isPng("not an image at all".toByteArray()))
        assertFalse(PngInspector.isPng(ByteArray(4)))
    }

    @Test
    fun onlyLoopbackMayUsePlainHttp() {
        assertEquals("http://127.0.0.1:7000", HostRemovalClient.normalizeHost("127.0.0.1:7000"))
        assertEquals("http://127.0.0.1:7000", HostRemovalClient.normalizeHost("http://127.0.0.1:7000/"))
        assertEquals("http://localhost:7000", HostRemovalClient.normalizeHost("http://localhost:7000"))
        assertNull(HostRemovalClient.normalizeHost("http://192.168.1.20:7000"))
        assertNull(HostRemovalClient.normalizeHost("   "))
        assertEquals("https://bg.example.com:8443", HostRemovalClient.normalizeHost("https://bg.example.com:8443"))
        assertTrue(HostRemovalClient.isPlainHttpHost("http://192.168.1.20:7000"))
        assertFalse(HostRemovalClient.isPlainHttpHost("http://127.0.0.1:7000"))
    }

    @Test
    fun buildsTheDocumentedEndpoint() {
        assertEquals("http://127.0.0.1:7000/remove-bg", HostRemovalClient.endpointFor("http://127.0.0.1:7000"))
        assertEquals("http://127.0.0.1:7000/remove-bg", HostRemovalClient.endpointFor("http://127.0.0.1:7000/"))
        assertEquals("http://127.0.0.1:7000/remove-bg", HostRemovalClient.endpointFor("http://127.0.0.1:7000/remove-bg"))
    }

    @Test
    fun treatsOnlyRealPngAnswersAsSuccess() {
        val png = pngWithColourType(6)
        val ok = HostRemovalClient.interpret(200, "image/png", png)
        assertNull(ok.message)
        assertEquals(png.size, ok.bytes?.size)
        assertEquals(BgRemovalErrors.BAD_RESPONSE, HostRemovalClient.interpret(200, "application/json", "{\"error\":\"nope\"}".toByteArray()).message)
        assertEquals(BgRemovalErrors.BAD_IMAGE, HostRemovalClient.interpret(415, "application/json", ByteArray(0)).message)
        assertTrue(HostRemovalClient.interpret(500, "text/plain", ByteArray(0)).message!!.contains("HTTP 500"))
        assertTrue(HostRemovalClient.interpret(404, "text/plain", ByteArray(0)).message!!.contains("/remove-bg"))
        assertTrue(HostRemovalClient.interpret(413, "text/plain", ByteArray(0)).message!!.contains("too large"))
    }

    @Test
    fun readsTheTerminalPythonAnswer() {
        assertNull(LocalPythonProbe.parseVersion("bash: python3: command not found"))
        assertEquals("Python 3.11.9", LocalPythonProbe.parseVersion("Python 3.11.9"))
        assertEquals("Python 3.12.0", LocalPythonProbe.parseVersion("python3 --version\nPython 3.12.0\n"))
        assertTrue(LocalPythonProbe.parseModuleOk("rembg OK", "rembg"))
        assertFalse(LocalPythonProbe.parseModuleOk("ModuleNotFoundError: No module named 'rembg'", "rembg"))
        assertTrue(LocalPythonProbe.looksMissingModule("ModuleNotFoundError: No module named 'onnxruntime'"))
    }

    @Test
    fun voiceCommandsReachTheRemoverInThreeLanguages() {
        val phrases = listOf(
            "remove the background",
            "jarvis remove background",
            "make the background transparent",
            "ei chobir background remove koro",
            "ছবিটার background transparent করে দাও",
            "এই ছবির ব্যাকগ্রাউন্ড রিমুভ করো",
            "background hata do",
            "बैकग्राउंड हटाओ"
        )
        for (phrase in phrases) {
            assertEquals("failed for: " + phrase, JarvisCommand.RemoveBackground, CommandParser.parse(phrase).command)
        }
    }

    @Test
    fun ordinaryCommandsAreNotHijacked() {
        assertFalse(CommandParser.parse("play a song on youtube").command is JarvisCommand.RemoveBackground)
        assertFalse(CommandParser.parse("turn on the flash").command is JarvisCommand.RemoveBackground)
        assertFalse(CommandParser.parse("what is the battery level").command is JarvisCommand.RemoveBackground)
    }
}
