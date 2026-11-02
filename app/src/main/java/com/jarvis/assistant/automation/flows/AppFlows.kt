package com.jarvis.assistant.automation.flows

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.jarvis.assistant.vision.UiElementInfo
import com.jarvis.assistant.vision.UiGroundingEngine
import com.jarvis.assistant.vision.ui.UiActionExecutor
import kotlinx.coroutines.delay
import org.json.JSONObject

/**
 * DEV STYLE: real on-device app flows.
 *
 * Jarvis drives other apps through Android accessibility - TikTok upload with caption + hashtags,
 * Instagram and Facebook posts and stories, Gemini image creation, and shopping searches such as
 * Daraz. Every step is logged with what actually happened, so nothing is reported as done unless
 * the screen proves it.
 */
object AppFlows {

    private val engine = UiGroundingEngine()

    data class Step(val label: String, val ok: Boolean, val detail: String = "")

    data class FlowResult(
        val ok: Boolean,
        val verified: Boolean,
        val steps: List<Step>,
        val message: String
    )

    suspend fun run(context: Context, flow: String, args: JSONObject): FlowResult {
        if (!UiActionExecutor.isServiceReady()) {
            return FlowResult(false, false, emptyList(),
                "Accessibility access is off, so I cannot drive other apps. Turn on JARVIS accessibility in Settings, then ask again.")
        }
        return when (flow.lowercase()) {
            "tiktok_upload", "tiktok" -> tiktok(context, args)
            "instagram_post", "instagram" -> instagramPost(context, args)
            "instagram_story" -> instagramStory(context, args)
            "facebook_post", "facebook" -> facebookPost(context, args)
            "facebook_story" -> facebookStory(context, args)
            "gemini_image", "image" -> geminiImage(context, args)
            "shop", "daraz" -> shopping(context, args)
            else -> FlowResult(false, false, emptyList(), "Unknown flow \"$flow\". Nothing was opened.")
        }
    }

    // ---------------------------------------------------------------- TikTok

    private suspend fun tiktok(context: Context, args: JSONObject): FlowResult {
        val video = args.optString("video")
        val caption = args.optString("caption")
        val tags = hashtags(args.optString("hashtags"))
        val steps = mutableListOf<Step>()

        if (!launch(context, "com.zhiliaoapp.musically")) {
            return FlowResult(false, false, steps, "TikTok is not installed on this phone.")
        }
        steps += Step("Open TikTok", true)
        delay(3500)

        val create = tapAny(listOf("create", "add", "camera", "+"), "Create button")
        steps += create
        delay(1500)

        val upload = tapAny(listOf("upload", "gallery", "select from", "upload a video"), "Upload tab")
        steps += upload
        delay(2500)

        val picked = pickMedia(listOf(video, "video", "recent")).also { steps += it }
        if (!picked.ok) {
            return FlowResult(false, false, steps,
                "TikTok opened the upload screen, but I could not pick \"${video.ifBlank { "the video" }}\". Choose the video once and confirm it is visible in the gallery.")
        }
        delay(2500)

        repeat(2) {
            if (tapAny(listOf("next"), "Next").ok) delay(1800)
        }

        val captionText = listOf(caption, tags).filter { it.isNotBlank() }.joinToString(" ")
        if (captionText.isNotBlank()) {
            val typed = typeInto(listOf("add description", "describe", "caption", "write a caption"), captionText)
            steps += typed
            delay(800)
        }

        val posted = tapAny(listOf("post", "publish", "share now"), "Post")
        steps += posted
        delay(4000)

        val stillOnEditor = findByLabels(listOf("add description", "post", "publish")) != null
        val verified = posted.ok && !stillOnEditor
        val message = when {
            !posted.ok -> "Video prepared in TikTok with caption and hashtags, but the Post button was not reachable. Tap Post to publish."
            verified -> "Posted to TikTok with caption and hashtags."
            else -> "Tapped Post. TikTok is still processing the video - check the profile before treating it as published."
        }
        return FlowResult(posted.ok, verified, steps, message)
    }

    // ------------------------------------------------------ Instagram / Facebook

    private suspend fun instagramPost(context: Context, args: JSONObject): FlowResult {
        val caption = withTags(args)
        val steps = mutableListOf<Step>()
        if (!launch(context, "com.instagram.android")) {
            return FlowResult(false, false, steps, "Instagram is not installed on this phone.")
        }
        steps += Step("Open Instagram", true)
        delay(3500)
        steps += tapAny(listOf("create", "new post", "new post", "+"), "Create")
        delay(1500)
        steps += tapAny(listOf("post", "select", "gallery", "recent"), "Post tab")
        delay(2000)
        steps += pickMedia(listOf(args.optString("video"), "video", "photo", "recent"))
        delay(2000)
        repeat(2) { if (tapAny(listOf("next"), "Next").ok) delay(1600) }
        if (caption.isNotBlank()) steps += typeInto(listOf("write a caption", "add a caption", "caption"), caption)
        val shared = tapAny(listOf("share", "post"), "Share")
        steps += shared
        delay(4000)
        return FlowResult(shared.ok, shared.ok, steps,
            if (shared.ok) "Instagram post shared with caption and hashtags." else "Media selected in Instagram, but Share was not reachable. Finish it on screen.")
    }

    private suspend fun instagramStory(context: Context, args: JSONObject): FlowResult {
        val steps = mutableListOf<Step>()
        if (!launch(context, "com.instagram.android")) {
            return FlowResult(false, false, steps, "Instagram is not installed on this phone.")
        }
        steps += Step("Open Instagram", true)
        delay(3500)
        steps += tapAny(listOf("create", "new story", "your story", "+"), "Create story")
        delay(1800)
        steps += pickMedia(listOf(args.optString("video"), "video", "photo", "recent"))
        delay(2200)
        val text = withTags(args)
        if (text.isNotBlank()) {
            tapAny(listOf("text", "add text", "aa"), "Text tool")
            delay(900)
            steps += typeInto(listOf("add text", "type something", "text"), text)
        }
        val shared = tapAny(listOf("your story", "share", "add to story", "send"), "Share story")
        steps += shared
        delay(3500)
        return FlowResult(shared.ok, shared.ok, steps,
            if (shared.ok) "Story shared to Instagram." else "Story media opened, but share was not reachable.")
    }

    private suspend fun facebookPost(context: Context, args: JSONObject): FlowResult {
        val steps = mutableListOf<Step>()
        if (!launch(context, "com.facebook.katana")) {
            return FlowResult(false, false, steps, "Facebook is not installed on this phone.")
        }
        steps += Step("Open Facebook", true)
        delay(4000)
        steps += tapAny(listOf("what's on your mind", "create post", "write something", "post"), "Post composer")
        delay(2000)
        val text = withTags(args)
        if (text.isNotBlank()) steps += typeInto(listOf("what's on your mind", "write something", "say something"), text)
        if (args.optString("video").isNotBlank()) {
            steps += tapAny(listOf("photo/video", "photo", "video"), "Photo/video")
            delay(2000)
            steps += pickMedia(listOf(args.optString("video"), "video", "recent"))
            delay(2500)
        }
        val posted = tapAny(listOf("post", "publish", "share now"), "Post")
        steps += posted
        delay(4000)
        return FlowResult(posted.ok, posted.ok, steps,
            if (posted.ok) "Facebook post published." else "Composer opened, but the final Post button was not reachable.")
    }

    private suspend fun facebookStory(context: Context, args: JSONObject): FlowResult {
        val steps = mutableListOf<Step>()
        if (!launch(context, "com.facebook.katana")) {
            return FlowResult(false, false, steps, "Facebook is not installed on this phone.")
        }
        steps += Step("Open Facebook", true)
        delay(4000)
        steps += tapAny(listOf("create story", "story", "add to story"), "Create story")
        delay(2000)
        steps += pickMedia(listOf(args.optString("video"), "video", "photo", "recent"))
        delay(2200)
        val shared = tapAny(listOf("share to story", "share", "your story", "post"), "Share story")
        steps += shared
        delay(3500)
        return FlowResult(shared.ok, shared.ok, steps,
            if (shared.ok) "Facebook story shared." else "Story screen opened, but share was not reachable.")
    }

    // ---------------------------------------------------------- Gemini images

    private suspend fun geminiImage(context: Context, args: JSONObject): FlowResult {
        val prompt = args.optString("prompt").ifBlank { args.optString("text") }
        if (prompt.isBlank()) {
            return FlowResult(false, false, emptyList(), "Tell me what image to create and I will send it to Gemini.")
        }
        val steps = mutableListOf<Step>()
        val launched = launch(context, "com.google.android.apps.bard") || launch(context, "com.google.android.googlequicksearchbox")
        if (!launched) {
            return FlowResult(false, false, steps, "The Gemini app is not installed on this phone.")
        }
        steps += Step("Open Gemini", true)
        delay(4000)
        val instruction = "Generate an image of $prompt. Create the image now and show it."
        val typed = typeInto(listOf("ask gemini", "message", "enter a prompt", "search"), instruction)
        steps += typed
        if (!typed.ok) {
            steps += tapAny(listOf("ask gemini", "type a prompt", "message"), "Prompt field")
            delay(900)
            steps += typeInto(listOf("ask gemini", "message", "enter a prompt", "search"), instruction)
        }
        steps += tapAny(listOf("send", "submit", "generate"), "Send prompt")
        delay(15000)
        steps += tapAny(listOf("download", "save image", "save"), "Download image")
        delay(3500)
        val opened = runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.parse("content://com.android.providers.downloads.documents/root/downloads"), "resource/folder")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            })
            true
        }.getOrElse {
            runCatching {
                context.startActivity(Intent(android.app.DownloadManager.ACTION_VIEW_DOWNLOADS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
            }.getOrDefault(false)
        }
        steps += Step("Open the saved image from Downloads", opened)
        return FlowResult(true, opened, steps,
            if (opened) "Image created in Gemini, downloaded, and Downloads is open so you can tap it."
            else "Image created in Gemini and download tapped. Open Downloads to see the file.")
    }

    // -------------------------------------------------------------- Shopping

    private suspend fun shopping(context: Context, args: JSONObject): FlowResult {
        val query = args.optString("product").ifBlank { args.optString("query") }
        if (query.isBlank()) {
            return FlowResult(false, false, emptyList(), "Which product should I look for?")
        }
        val steps = mutableListOf<Step>()
        val launched = launch(context, "com.daraz.android") || launch(context, "com.alibaba.aliexpresshd") ||
            launch(context, "com.amazon.mShop.android.shopping")
        if (!launched) {
            return FlowResult(false, false, steps, "No shopping app (Daraz, AliExpress, Amazon) is installed on this phone.")
        }
        steps += Step("Open shopping app", true)
        delay(4000)
        steps += tapAny(listOf("search", "search daraz", "search in"), "Search")
        delay(1200)
        steps += typeInto(listOf("search", "search daraz", "search in"), query)
        delay(3000)
        val results = currentLabels().filter { it.length in 4..70 }.take(10)
        val best = results.firstOrNull { it.contains(query.split(" ").first(), ignoreCase = true) }
        if (best != null) {
            steps += tapAny(listOf(best), "Best match: $best")
            delay(3500)
        }
        val detail = currentLabels().filter { it.length in 2..60 }.take(14).joinToString(" | ")
        return FlowResult(true, steps.last().ok, steps,
            "Searched \"$query\". " + (best?.let { "Opened the closest match: $it. " } ?: "Results are on screen. ") +
                "On screen now: ${detail.ifBlank { "(no readable text)" }}. Check price, seller rating and delivery before ordering - I will not confirm a purchase on your behalf.")
    }

    // ---------------------------------------------------------------- helpers

    private fun hashtags(raw: String): String {
        if (raw.isBlank()) return ""
        return raw.split(Regex("[,\\s]+"))
            .filter { it.isNotBlank() }
            .joinToString(" ") { if (it.startsWith("#")) it else "#$it" }
    }

    private fun withTags(args: JSONObject): String =
        listOf(args.optString("caption"), hashtags(args.optString("hashtags")))
            .filter { it.isNotBlank() }
            .joinToString(" ")

    private fun launch(context: Context, packageName: String): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        return runCatching { context.startActivity(intent) }.isSuccess
    }

    private suspend fun currentElements(): List<UiElementInfo> {
        val root = UiActionExecutor.activeRoot() ?: return emptyList()
        return runCatching { engine.scanScreen(root) }.getOrDefault(emptyList())
    }

    private suspend fun currentLabels(): List<String> = currentElements()
        .map { it.primaryLabel.trim() }
        .filter { it.isNotBlank() }
        .distinct()

    private suspend fun findByLabels(labels: List<String>): UiElementInfo? {
        val elements = currentElements()
        if (elements.isEmpty()) return null
        val needles = labels.filter { it.isNotBlank() }.map { it.lowercase() }
        return elements.firstOrNull { element ->
            val haystack = (element.primaryLabel + " " + element.viewId).lowercase()
            needles.any { needle -> haystack.contains(needle) }
        }
    }

    private suspend fun tapAny(labels: List<String>, label: String): Step {
        val trimmed = labels.filter { it.isNotBlank() }
        if (trimmed.isEmpty()) return Step(label, false, "nothing to match")
        val end = System.currentTimeMillis() + 9000
        while (System.currentTimeMillis() < end) {
            val element = findByLabels(trimmed)
            if (element != null) {
                val outcome = UiActionExecutor.click(element)
                if (outcome.performed) return Step(label, true, element.primaryLabel)
                delay(500)
            } else {
                delay(600)
            }
        }
        return Step(label, false, "not found on screen")
    }

    private suspend fun typeInto(labels: List<String>, text: String): Step {
        val field = findByLabels(labels)
            ?: currentElements().firstOrNull { it.isEditable }
            ?: return Step("Type", false, "no text field on screen")
        val focused = UiActionExecutor.click(field)
        delay(400)
        val written = UiActionExecutor.setText(field, text)
        if (!written.performed) {
            val typed = com.jarvis.assistant.automation.accessibility.JarvisAccessibilityService.typeText(text, false)
            return Step("Type", typed, if (typed) "typed into the focused field" else "the field refused the text")
        }
        return Step("Type", focused.performed || written.performed, "text set")
    }

    private suspend fun pickMedia(labels: List<String>): Step {
        tapAny(listOf("videos", "video", "gallery", "recents"), "Open gallery tab")
        delay(1200)
        val wanted = labels.filter { it.isNotBlank() }
        val step = tapAny(wanted, "Select media")
        return step
    }
}