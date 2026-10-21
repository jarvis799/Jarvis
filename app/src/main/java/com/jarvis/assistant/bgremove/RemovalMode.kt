package com.jarvis.assistant.bgremove

/** Where a background removal actually runs. Never guessed - always probed or configured. */
enum class RemovalMode { LOCAL_DEVICE_SERVER, CONFIGURED_HOST, UNAVAILABLE }

data class RemovalPlan(val mode: RemovalMode, val endpoint: String, val note: String) {
    val isLocal: Boolean get() = mode == RemovalMode.LOCAL_DEVICE_SERVER
}

/** Actionable messages only, so the user is told what to do next instead of "failed". */
object BgRemovalErrors {
    const val PYTHON_MISSING = "Python is not available in the Jarvis terminal yet. Open the terminal once so the Linux environment finishes setting up, then try again."
    const val REMBG_MISSING = "Python runs, but the rembg package is not installed. On Android's Alpine runtime the rembg / onnxruntime wheels are usually unavailable, so run the host on a computer or start it inside the terminal if pip succeeds."
    const val HOST_NOT_CONFIGURED = "No background removal host is configured. Start the Jarvis host and paste its URL, then test the connection."
    const val HOST_UNREACHABLE = "No background removal host answered. Start the host, or paste its URL, then test the connection again."
    const val PLAIN_HTTP_BLOCKED = "That address would send the photo over the network without encryption. Use an https URL, or 127.0.0.1 when the host runs on this phone."
    const val BAD_IMAGE = "That file is not a supported image. Use PNG, JPEG or WebP up to 12 MB."
    const val TOO_LARGE = "That image is larger than 12 MB. Pick a smaller one."
    const val HOST_ERROR = "The host could not remove the background."
    const val BAD_RESPONSE = "The host replied, but the result was not a transparent PNG."
    const val NO_TRANSPARENCY = "The result has no transparent pixels, so the background was not removed. Try another image or another host."
    const val TOO_BIG_TO_CHECK = "The result was too large for this phone to verify. Open the preview to check it before saving."
    const val OUT_OF_MEMORY = "This phone ran out of memory on that image. Try a smaller photo."
    const val SAVE_FAILED = "The PNG could not be saved to the gallery."
    const val CANCELLED = "Cancelled."
}

sealed class RemovalResult {
    data class Success(val pngFile: java.io.File, val plan: RemovalPlan, val transparentRatio: Float, val alphaVerified: Boolean) : RemovalResult()
    data class Failure(val message: String, val plan: RemovalPlan?) : RemovalResult()
    data class Cancelled(val plan: RemovalPlan?) : RemovalResult()
}
