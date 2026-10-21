package com.jarvis.assistant.bgremove

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color

/** Reads what a PNG really declares, so a non-transparent reply is never reported as success. */
object PngInspector {

    private val SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)

    fun isPng(bytes: ByteArray): Boolean {
        if (bytes.size < 34) return false
        for (index in SIGNATURE.indices) if (bytes[index] != SIGNATURE[index]) return false
        return String(bytes, 12, 4, Charsets.ISO_8859_1) == "IHDR"
    }

    /** IHDR colour type 4 or 6, or a tRNS chunk, means the file can carry transparency. */
    fun declaresAlpha(bytes: ByteArray): Boolean {
        if (!isPng(bytes)) return false
        val colourType = bytes[25].toInt() and 0xFF
        if (colourType == 4 || colourType == 6) return true
        val scanLength = minOf(bytes.size, 64 * 1024)
        return String(bytes, 26, scanLength - 26, Charsets.ISO_8859_1).contains("tRNS")
    }
}

/** Samples the decoded pixels and reports how much of the image is actually see-through. */
object AlphaCoverage {

    const val MAX_VERIFY_BYTES = 6L * 1024 * 1024
    const val MIN_RATIO = 0.01f

    fun transparentRatio(bytes: ByteArray): Float? {
        val options = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null
        var clear = 0
        var total = 0
        val step = maxOf(1, minOf(bitmap.width, bitmap.height) / 64)
        var y = 0
        while (y < bitmap.height) {
            var x = 0
            while (x < bitmap.width) {
                if (Color.alpha(bitmap.getPixel(x, y)) == 0) clear++
                total++
                x += step
            }
            y += step
        }
        bitmap.recycle()
        return if (total == 0) null else clear.toFloat() / total
    }
}
