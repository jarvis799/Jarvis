package com.jarvis.assistant.audio

import java.io.File
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class VoskModelCacheTest {
    private fun withCache(test: (File) -> Unit) {
        val root = Files.createTempDirectory("wake-model-test").toFile()
        try { test(root) } finally { root.deleteRecursively() }
    }
    private fun fill(root: File) { VoskModelCache.requiredFiles.forEach {
        File(root, it).apply { parentFile?.mkdirs(); writeText("test") }
    } }
    @Test fun acousticFileAloneIsNotACompleteModel() = withCache { root ->
        File(root, "am/final.mdl").apply { parentFile?.mkdirs(); writeText("test") }
        assertFalse(VoskModelCache.isComplete(root, "bundle"))
    }
    @Test fun bundleChangeAndMissingGraphInvalidateCache() = withCache { root ->
        fill(root); VoskModelCache.markComplete(root, "bundle")
        assertTrue(VoskModelCache.isComplete(root, "bundle"))
        assertFalse(VoskModelCache.isComplete(root, "new-bundle"))
        File(root, "graph/Gr.fst").delete()
        assertFalse(VoskModelCache.isComplete(root, "bundle"))
    }
    @Test(expected = IllegalStateException::class) fun incompleteExtractionCannotBeCommitted() = withCache { root ->
        VoskModelCache.markComplete(root, "bundle")
    }
}
