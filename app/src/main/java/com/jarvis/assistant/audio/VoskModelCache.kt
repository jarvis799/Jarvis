package com.jarvis.assistant.audio

import java.io.File

/** Completion is recorded only after every required file and the native model have loaded. */
object VoskModelCache {
    val requiredFiles = listOf(
        "am/final.mdl", "conf/mfcc.conf", "conf/model.conf", "graph/Gr.fst", "graph/HCLr.fst",
        "graph/disambig_tid.int", "graph/phones/word_boundary.int", "ivector/final.dubm",
        "ivector/final.ie", "ivector/final.mat", "ivector/global_cmvn.stats",
        "ivector/online_cmvn.conf", "ivector/splice.conf"
    )
    fun isComplete(root: File, bundleId: String): Boolean =
        bundleId.isNotBlank() && File(root, ".complete").let { it.isFile && it.readText() == bundleId } &&
            requiredFiles.all { File(root, it).let { f -> f.isFile && f.length() > 0L } }

    fun markComplete(root: File, bundleId: String) {
        check(bundleId.isNotBlank() && requiredFiles.all { File(root, it).let { f -> f.isFile && f.length() > 0L } }) {
            "Offline wake model extraction is incomplete"
        }
        val temporary = File(root, ".complete.tmp")
        temporary.writeText(bundleId)
        check(temporary.renameTo(File(root, ".complete"))) { "Cannot save wake model completion marker" }
    }
}
