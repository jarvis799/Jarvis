package com.jarvis.assistant.vision.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.jarvis.assistant.automation.accessibility.JarvisAccessibilityService
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * On-demand, on-device OCR of the current screen. Used only when the accessibility
 * tree cannot ground a target, so no screenshot is taken while idle.
 *
 * Black frames are detected and reported instead of being treated as an empty screen:
 * Android returns a black buffer for protected/secure windows, which is a real
 * limitation and is surfaced to the user as one.
 */
object UiScreenOcr {

    private const val TAG = "UiScreenOcr"
    private const val MAX_LINES = 120

    data class OcrLine(val text: String, val bounds: Rect, val confidence: Float = 0f)

    data class OcrRead(
        val lines: List<OcrLine> = emptyList(),
        val blocked: Boolean = false,
        val reason: String? = null,
        val bitmapWidth: Int = 0,
        val bitmapHeight: Int = 0
    )

    suspend fun readScreen(): OcrRead {
        if (!JarvisAccessibilityService.isEnabled()) {
            return OcrRead(blocked = true, reason = "The Jarvis accessibility service is not running, so the screen cannot be read.")
        }
        val jpeg = JarvisAccessibilityService.captureScreenJpeg(80)
            ?: return OcrRead(blocked = true, reason = "Android did not return a screenshot. If a screen-recording consent dialog appears, allow it once.")
        val bitmap = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)
            ?: return OcrRead(blocked = true, reason = "The captured screenshot could not be decoded.")
        if (looksBlack(bitmap)) {
            val size = bitmap.width to bitmap.height
            bitmap.recycle()
            Log.w(TAG, "Black frame detected (" + size.first + "x" + size.second + ")")
            return OcrRead(blocked = true, reason = "Android returned a black frame - this screen is protected or capture is blocked here.")
        }
        val lines = recognize(bitmap)
        val result = OcrRead(lines, false, null, bitmap.width, bitmap.height)
        bitmap.recycle()
        Log.i(TAG, "OCR read " + lines.size + " text lines")
        return result
    }

    private suspend fun recognize(bitmap: Bitmap): List<OcrLine> = suspendCancellableCoroutine { cont ->
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            recognizer.process(InputImage.fromBitmap(bitmap, 0))
                .addOnSuccessListener { text ->
                    val out = ArrayList<OcrLine>()
                    for (block in text.textBlocks) {
                        for (line in block.lines) {
                            val bounds = line.boundingBox ?: continue
                            val value = line.text.trim()
                            if (value.isNotEmpty()) out += OcrLine(value, Rect(bounds), line.confidence)
                            if (out.size >= MAX_LINES) break
                        }
                        if (out.size >= MAX_LINES) break
                    }
                    recognizer.close()
                    if (cont.isActive) cont.resume(out)
                }
                .addOnFailureListener { error ->
                    Log.w(TAG, "Text recognition failed: " + error.message)
                    recognizer.close()
                    if (cont.isActive) cont.resume(emptyList<OcrLine>())
                }
        } catch (e: Exception) {
            Log.e(TAG, "Text recognizer error: " + e.message)
            runCatching { recognizer.close() }
            if (cont.isActive) cont.resume(emptyList<OcrLine>())
        }
    }

    /** A frame that is (almost) uniformly near-black is a blocked capture, not an empty screen. */
    private fun looksBlack(bitmap: Bitmap): Boolean {
        val stepX = maxOf(1, bitmap.width / 24)
        val stepY = maxOf(1, bitmap.height / 32)
        var samples = 0
        var dark = 0
        var y = 0
        while (y < bitmap.height) {
            var x = 0
            while (x < bitmap.width) {
                val pixel = bitmap.getPixel(x, y)
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                samples++
                if (r < 26 && g < 26 && b < 26) dark++
                x += stepX
            }
            y += stepY
        }
        return samples > 0 && dark.toFloat() / samples.toFloat() > 0.985f
    }
}
