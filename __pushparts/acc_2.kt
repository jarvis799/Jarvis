ribed
        if (looksLikeSubscribeButton && (node.isClickable || node.parent?.isClickable == true)) {
            return node
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findSubscribeNodeInTree(child)
            if (result != null) return result
        }
        return null
    }

    // -------------------------------------------------------------
    // YouTube Fullscreen Toggle
    // -------------------------------------------------------------

    fun armYouTubeFullscreen() {
        isWaitingForYouTubeFullscreen = true
        youTubeFullscreenArmTime = System.currentTimeMillis()
        Log.i(TAG, "Armed for YouTube fullscreen toggle")
        handler.postDelayed({ pollYouTubeFullscreen(1) }, 400)
    }

    private fun pollYouTubeFullscreen(attempt: Int) {
        if (!isWaitingForYouTubeFullscreen || attempt > 8) {
            isWaitingForYouTubeFullscreen = false
            return
        }
        if (!attemptYouTubeFullscreen(attempt)) {
            handler.postDelayed({ pollYouTubeFullscreen(attempt + 1) }, 450)
        }
    }

    private fun attemptYouTubeFullscreen(attempt: Int): Boolean {
        val rootNode = rootInActiveWindow ?: return false

        val fsIds = listOf(
            "com.google.android.youtube:id/fullscreen_button",
            "app.revanced.android.youtube:id/fullscreen_button"
        )
        for (id in fsIds) {
            val nodes = rootNode.findAccessibilityNodeInfosByViewId(id)
            for (node in nodes) {
                if (clickNodeAndTap(node)) {
                    Log.i(TAG, "Clicked YouTube fullscreen button by ID: $id")
                    isWaitingForYouTubeFullscreen = false
                    return true
                }
            }
        }

        val descMatch = findByContentDescriptionContains(
            rootNode,
            listOf("enter full screen", "full screen", "fullscreen")
        )
        if (descMatch != null && clickNodeAndTap(descMatch)) {
            Log.i(TAG, "Clicked YouTube fullscreen via content description fallback")
            isWaitingForYouTubeFullscreen = false
            return true
        }

        // The fullscreen control is usually hidden until the player controls are revealed.
        // Tap the center of the screen on the first couple attempts to reveal them, then retry.
        if (attempt <= 3) {
            val rect = Rect()
            rootNode.getBoundsInScreen(rect)
            if (rect.width() > 0 && rect.height() > 0) {
                clickAt(rect.centerX().toFloat(), rect.centerY().toFloat())
            }
        }

        return false
    }

    private fun findByContentDescriptionContains(node: AccessibilityNodeInfo, phrases: List<String>): AccessibilityNodeInfo? {
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""
        if (desc.isNotBlank() && phrases.any { desc.contains(it) }) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findByContentDescriptionContains(child, phrases)
            if (result != null) return result
        }
        return null
    }

    // -------------------------------------------------------------
    // WhatsApp Delete Message (Delete for Everyone / Delete for Me)
    // -------------------------------------------------------------

    fun armWhatsAppDelete(forEveryone: Boolean) {
        isWaitingForWhatsAppDelete = true
        deleteForEveryone = forEveryone
        whatsAppDeleteArmTime = System.currentTimeMillis()
        deleteStage = 0
        Log.i(TAG, "Armed for WhatsApp delete message (forEveryone=$forEveryone)")
        handler.postDelayed({ pollWhatsAppDelete(1) }, 900)
    }

    private fun pollWhatsAppDelete(attempt: Int) {
        if (!isWaitingForWhatsAppDelete || attempt > 20) {
            isWaitingForWhatsAppDelete = false
            return
        }
        val done = attemptWhatsAppDeleteAction()
        if (!done) {
            handler.postDelayed({ pollWhatsAppDelete(attempt + 1) }, 550)
        }
    }

    private fun attemptWhatsAppDeleteAction(): Boolean {
        val rootNode = getActiveRootNode() ?: return false

        return when (deleteStage) {
            0 -> {
                // Stage 0: long-press the most recent message bubble to open the selection menu.
                val messageIds = listOf(
                    "com.whatsapp:id/message_text",
                    "com.whatsapp.w4b:id/message_text",
                    "com.whatsapp:id/conversation_text_row",
                    "com.whatsapp.w4b:id/conversation_text_row"
                )
                var target: AccessibilityNodeInfo? = null
                for (id in messageIds) {
                    val nodes = rootNode.findAccessibilityNodeInfosByViewId(id)
                    if (nodes.isNotEmpty()) target = nodes.last()
                }
                if (target == null) {
                    target = findLastMessageBubble(rootNode)
                }
                if (target != null) {
                    var longPressed = target.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)
                    if (!longPressed) {
                        val rect = Rect()
                        target.getBoundsInScreen(rect)
                        if (rect.width() > 0 && rect.height() > 0) {
                            longPressAt(rect.centerX().toFloat(), rect.centerY().toFloat())
                            longPressed = true
                        }
                    }
                    if (longPressed) {
                        Log.i(TAG, "Long-pressed last WhatsApp message bubble")
                        deleteStage = 1
                    }
                }
                false
            }
            1 -> {
                // Stage 1: tap the delete (trash) icon in the selection action bar.
                val deleteIds = listOf(
                    "com.whatsapp:id/menuitem_delete",
                    "com.whatsapp.w4b:id/menuitem_delete"
                )
                var clicked = false
                for (id in deleteIds) {
                    val nodes = rootNode.findAccessibilityNodeInfosByViewId(id)
                    for (node in nodes) {
                        if (clickNodeAndTap(node)) {
                            Log.i(TAG, "Clicked WhatsApp delete icon")
                            clicked = true
                            break
                        }
                    }
                    if (clicked) break
                }
                if (!clicked) {
                    val descNode = findByContentDescriptionContains(rootNode, listOf("delete"))
                    if (descNode != null && clickNodeAndTap(descNode)) {
                        Log.i(TAG, "Clicked WhatsApp delete icon via content description")
                        clicked = true
                    }
                }
                if (clicked) deleteStage = 2
                false
            }
            2 -> {
                // Stage 2: choose "Delete for everyone" or "Delete for me" in the dialog.
                val optionText = if (deleteForEveryone) "Delete for everyone" else "Delete for me"
                var clicked = clickFirstMatchingText(rootNode, optionText)
                if (!clicked && deleteForEveryone) {
                    // Some WhatsApp versions phrase it slightly differently.
                    clicked = clickFirstMatchingText(rootNode, "everyone")
                }
                if (!clicked && !deleteForEveryone) {
                    clicked = clickFirstMatchingText(rootNode, "delete for me")
                }
                if (clicked) {
                    Log.i(TAG, "Selected WhatsApp delete option: $optionText")
                    deleteStage = 3
                }
                false
            }
            3 -> {
                // Stage 3: some versions show one more confirmation ("Delete") button.
                val confirmed = clickFirstMatchingText(rootNode, "delete") ||
                    rootNode.findAccessibilityNodeInfosByViewId("android:id/button1").any { clickNodeAndTap(it) }
                if (confirmed) {
                    Log.i(TAG, "Confirmed WhatsApp message deletion")
                }
                isWaitingForWhatsAppDelete = false
                true
            }
            else -> {
                isWaitingForWhatsAppDelete = false
                true
            }
        }
    }

    private fun findLastMessageBubble(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var result: AccessibilityNodeInfo? = null
        val viewId = node.viewIdResourceName ?: ""
        if (viewId.contains("message_text") || viewId.contains("conversation_text_row")) {
            result = node
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val childResult = findLastMessageBubble(child)
            if (childResult != null) result = childResult
        }
        return result
    }

    private fun clickFirstMatchingText(root: AccessibilityNodeInfo, text: String): Boolean {
        val nodes = root.findAccessibilityNodeInfosByText(text)
        for (node in nodes) {
            if (node.isClickable || node.parent?.isClickable == true) {
                if (clickNodeAndTap(node)) return true
            }
        }
        return false
    }

    private fun longPressAt(x: Float, y: Float) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                val path = Path().apply { moveTo(x, y) }
                val gesture = GestureDescription.Builder()
                    .addStroke(GestureDescription.StrokeDescription(path, 0, 700))
                    .build()
                dispatchGesture(gesture, null, null)
            } catch (e: Exception) {
                Log.d(TAG, "Long-press gesture failed: ${e.message}")
            }
        }
    }

    // -------------------------------------------------------------
    // Click & Touch Helpers
    // -------------------------------------------------------------

    fun clickNodeAndTap(node: AccessibilityNodeInfo): Boolean {
        // Step 1: Attempt programmatic accessibility click on the node directly
        if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            Log.d(TAG, "Programmatic ACTION_CLICK succeeded on node")
            return true
        }

        // Step 2: Attempt programmatic accessibility click on clickable parent hierarchy
        var parent = node.parent
        var depth = 0
        while (parent != null && depth < 3) {
            if (parent.isClickable && parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                Log.d(TAG, "Programmatic ACTION_CLICK succeeded on parent at depth $depth")
                return true
            }
            parent = parent.parent
            depth++
        }

        // Step 3: ONLY fallback to coordinate gesture tap if programmatic clicks failed or were not clickable
        val rect = Rect()
        node.getBoundsInScreen(rect)
        if (rect.width() > 0 && rect.height() > 0) {
            Log.d(TAG, "Falling back to coordinate gesture tap at center: (${rect.centerX()}, ${rect.centerY()})")
            return clickAt(rect.centerX().toFloat(), rect.centerY().toFloat())
        }

        return false
    }

    fun clickAt(x: Float, y: Float): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                val path = Path().apply { moveTo(x, y) }
                val gesture = GestureDescription.Builder()
                    .addStroke(GestureDescription.StrokeDescription(path, 0, 50))
                    .build()
                return dispatchGesture(gesture, null, null)
            } catch (e: Exception) {
                Log.d(TAG, "Gesture click at ($x, $y) failed: ${e.message}")
            }
        }
        return false
    }

    // ---------------------------------------------------------------
    // Reference YouTube & Accessibility Actions
    // ---------------------------------------------------------------

    /** Taps the "Skip Ad" / "Skip Ads" button if currently visible. Returns true if found and tapped. */
    fun skipAd(): Boolean = clickNodeMatching("skip ad")

    /** Taps the Like button on the currently open video. */
    fun likeVideo(): Boolean = clickNodeMatching("like", exclude = "dislike")

    /** Taps the Subscribe button on the current channel/video. */
    fun subscribeChannel(): Boolean = clickNodeMatching("subscribe")

    /**
     * Opens the channel page by tapping the channel avatar/name row under the
     * video (NOT the "Subscribe" button).
     */
    fun openChannel(): Boolean {
        val root = rootInActiveWindow ?: return false
        val candidates = mutableListOf<AccessibilityNodeInfo>()
        collectByResourceId(root, "channel", candidates)
        val target = candidates
            .filter { it.isClickable }
            .minByOrNull { nodeLabel(it).length }
        if (target != null) return target.performAction(AccessibilityNodeInfo.ACTION_CLICK)

        return clickNodeMatching("channel", exclude = "subscribe")
    }

    /**
     * Taps the fullscreen toggle button (works for both entering and exiting fullscreen).
     */
    fun toggleFullscreen(): Boolean {
        if (clickNodeMatching("full screen", "fullscreen")) return true

        val root = rootInActiveWindow ?: return false
        val candidates = mutableListOf<AccessibilityNodeInfo>()
        collectByResourceId(root, "fullscreen", candidates)
        val target = candidates.firstOrNull { it.isClickable } ?: return false
        return target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    /** Auto-clicks first video result when YouTube search opens. */
    fun clickFirstVideoResult(): Boolean {
        val root = rootInActiveWindow ?: return false
        val candidates = mutableListOf<AccessibilityNodeInfo>()
        collectClickableVideoNodes(root, candidates)
        if (candidates.isNotEmpty()) {
            return candidates.first().performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
        return tapAtPercentage(50f, 30f)
    }

    private fun collectClickableVideoNodes(
        node: AccessibilityNodeInfo,
        out: MutableList<AccessibilityNodeInfo>
    ) {
        if (node.isClickable && (node.className?.contains("ViewGroup") == true || node.className?.contains("RelativeLayout") == true || node.className?.contains("FrameLayout") == true)) {
            val label = nodeLabel(node)
            if (label.length > 20) out.add(node)
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectClickableVideoNodes(child, out)
        }
    }

    private fun clickNodeMatching(vararg keywords: String, exclude: String? = null): Boolean {
        return try {
            val root = rootInActiveWindow ?: return false
            val lowerKeywords = keywords.map { it.lowercase() }
            val lowerExclude = exclude?.lowercase()

            val candidates = mutableListOf<AccessibilityNodeInfo>()
            collectMatches(root, lowerKeywords, lowerExclude, candidates)
            if (candidates.isEmpty()) return false

            val target = candidates.minByOrNull { nodeLabel(it).length } ?: return false
            target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        } catch (e: Exception) {
            Log.w(TAG, "clickNodeMatching exception", e)
            false
        }
    }

    private fun nodeLabel(node: AccessibilityNodeInfo): String =
        node.text?.toString() ?: node.contentDescription?.toString() ?: ""

    private fun collectMatches(
        node: AccessibilityNodeInfo,
        keywords: List<St