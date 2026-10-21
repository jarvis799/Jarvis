ring>,
        exclude: String?,
        out: MutableList<AccessibilityNodeInfo>
    ) {
        try {
            val label = nodeLabel(node).lowercase()
            if (label.isNotBlank()) {
                val matches = keywords.any { label.contains(it) } && (exclude == null || !label.contains(exclude))
                if (matches && (node.isClickable || node.isCheckable)) {
                    out.add(node)
                }
            }

            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                collectMatches(child, keywords, exclude, out)
            }
        } catch (e: Exception) {
            Log.w(TAG, "collectMatches exception", e)
        }
    }

    private fun collectByResourceId(
        node: AccessibilityNodeInfo,
        needle: String,
        out: MutableList<AccessibilityNodeInfo>
    ) {
        try {
            val id = node.viewIdResourceName?.lowercase() ?: ""
            if (id.contains(needle)) out.add(node)
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                collectByResourceId(child, needle, out)
            }
        } catch (e: Exception) {
            Log.w(TAG, "collectByResourceId exception", e)
        }
    }

    // ---------------------------------------------------------------
    // WhatsApp Clean Automation (Fixed: Immediately stops, never clicks mic)
    // ---------------------------------------------------------------

    /**
     * Monitors the screen after a WhatsApp deep-link is launched and automatically taps the Send button
     * (content-desc="Send" or id="send" or send icon) for hands-free message delivery.
     * Stops polling immediately after clicking to avoid clicking the microphone icon.
     */
    fun scheduleWhatsAppAutoSend() {
        val mainHandler = Handler(Looper.getMainLooper())
        mainHandler.postDelayed(object : Runnable {
            var attempts = 0
            override fun run() {
                val root = rootInActiveWindow
                if (root != null) {
                    val sendNode = findWhatsAppSendButton(root)
                    if (sendNode != null) {
                        tapNodeOrParent(sendNode)
                        Log.d(TAG, "Successfully tapped WhatsApp send button on attempt $attempts. Disarming.")
                        return
                    }
                }

                attempts++
                if (attempts < 14) {
                    mainHandler.postDelayed(this, 300)
                }
            }
        }, 500)
    }

    private fun findWhatsAppSendButton(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""
        val text = node.text?.toString()?.lowercase() ?: ""
        val viewId = node.viewIdResourceName?.lowercase() ?: ""

        if (desc == "send" || desc == "send message" || text == "send" || viewId.endsWith(":id/send")) {
            return node
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findWhatsAppSendButton(child)
            if (result != null) return result
        }
        return null
    }

    fun scheduleWhatsAppCallAutoTap(callType: String) {
        val isVideo = callType.lowercase().contains("video")
        val mainHandler = Handler(Looper.getMainLooper())
        mainHandler.postDelayed(object : Runnable {
            var attempts = 0
            var callButtonTapped = false

            override fun run() {
                val root = rootInActiveWindow
                if (root != null) {
                    if (!callButtonTapped) {
                        val callNode = findWhatsAppCallButton(root, isVideo)
                        if (callNode != null) {
                            val success = tapDirectOrImmediateClickable(callNode)
                            if (success) {
                                callButtonTapped = true
                                Log.d(TAG, "Tapped WhatsApp call button, watching for confirmation dialog...")
                            }
                        }
                    } else {
                        val confirmNode = findWhatsAppCallConfirmButton(root)
                        if (confirmNode != null) {
                            tapNodeOrParent(confirmNode)
                            Log.d(TAG, "Tapped WhatsApp Call confirmation dialog button!")
                            return
                        }
                    }
                }

                attempts++
                if (attempts < 16) {
                    mainHandler.postDelayed(this, 350)
                }
            }
        }, 500)
    }

    private fun tapDirectOrImmediateClickable(node: AccessibilityNodeInfo): Boolean {
        if (node.isClickable) {
            val ok = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            if (ok) return true
        }
        val parent = node.parent
        if (parent != null && parent.isClickable) {
            val parentId = parent.viewIdResourceName?.lowercase() ?: ""
            if (!parentId.contains("header") && !parentId.contains("action_bar") && !parentId.contains("toolbar")) {
                val ok = parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                if (ok) return true
            }
        }
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        if (bounds.width() > 0 && bounds.height() > 0) {
            return tapAtAbsoluteCoordinates(bounds.centerX().toFloat(), bounds.centerY().toFloat())
        }
        return false
    }

    private fun findWhatsAppCallConfirmButton(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val text = node.text?.toString()?.trim()?.lowercase() ?: ""
        val desc = node.contentDescription?.toString()?.trim()?.lowercase() ?: ""
        val viewId = node.viewIdResourceName?.lowercase() ?: ""

        if (viewId.endsWith(":id/button1") ||
            text == "call" || text == "video call" || text == "start call" || text == "कॉल करें" || text == "कॉल" ||
            desc == "call" || desc == "video call" || desc == "start call") {
            if (node.isClickable || node.parent?.isClickable == true) {
                return node
            }
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val res = findWhatsAppCallConfirmButton(child)
            if (res != null) return res
        }
        return null
    }

    private fun findWhatsAppCallButton(node: AccessibilityNodeInfo, isVideo: Boolean): AccessibilityNodeInfo? {
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""
        val viewId = node.viewIdResourceName?.lowercase() ?: ""

        if (desc.contains("profile") || desc.contains("photo") || desc.contains("picture") ||
            desc.contains("avatar") || desc.contains("header") || desc.contains("navigate up") ||
            desc.contains("back") || viewId.contains("picture") || viewId.contains("avatar")) {
            return null
        }

        val isTarget = if (isVideo) {
            (desc.contains("video call") || viewId.endsWith(":id/video_call") || viewId.endsWith(":id/menuitem_video_call")) &&
                    !desc.contains("voice")
        } else {
            (desc == "voice call" || desc == "call" || desc.startsWith("voice call") ||
                    viewId.endsWith(":id/voice_call") || viewId.endsWith(":id/menuitem_call")) &&
                    !desc.contains("video")
        }

        if (isTarget && (node.isClickable || node.parent?.isClickable == true)) {
            return node
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findWhatsAppCallButton(child, isVideo)
            if (result != null) return result
        }
        return null
    }

    private fun tapNodeOrParent(node: AccessibilityNodeInfo): Boolean {
        var current: AccessibilityNodeInfo? = node
        while (current != null) {
            if (current.isClickable) {
                val ok = current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                if (ok) return true
            }
            current = current.parent
        }
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        if (bounds.width() > 0 && bounds.height() > 0) {
            return tapAtAbsoluteCoordinates(bounds.centerX().toFloat(), bounds.centerY().toFloat())
        }
        return false
    }

    fun tapAtPercentage(xPercent: Float, yPercent: Float): Boolean {
        val dm = resources.displayMetrics
        val x = (xPercent / 100f) * dm.widthPixels
        val y = (yPercent / 100f) * dm.heightPixels
        clickAt(x, y)
        return true
    }

    fun tapAtAbsoluteCoordinates(x: Float, y: Float): Boolean {
        clickAt(x, y)
        return true
    }

    fun clickNodeWithText(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        return clickFirstMatchingText(root, text)
    }

    fun typeText(textToType: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (focused != null && focused.isEditable) {
            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, textToType)
            }
            return focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        }
        return false
    }

    fun getActiveRootNode(): AccessibilityNodeInfo? {
        // Prefer Android's active-window root. If unavailable, only accept a window
        // that Android explicitly marks focused/active; never guess with an arbitrary
        // application window because dialogs, keyboards, and split-screen can reorder it.
        val active = rootInActiveWindow
        if (active != null) return active

        // 2. Fallback to focused application window
        try {
            val focusedAppWin = windows.firstOrNull {
                it.type == AccessibilityWindowInfo.TYPE_APPLICATION && it.isFocused
            }
            if (focusedAppWin?.root != null) return focusedAppWin.root

            // 3. Fallback to active application window
            val activeAppWin = windows.firstOrNull {
                it.type == AccessibilityWindowInfo.TYPE_APPLICATION && it.isActive
            }
            if (activeAppWin?.root != null) return activeAppWin.root

            return null
        } catch (e: Exception) {
            Log.w(TAG, "Error resolving active root node across windows: ${e.message}")
            return null
        }
    }

    fun getAppRootNode(): AccessibilityNodeInfo? {
        return getActiveRootNode()
    }

    fun performClickAtRect(rect: Rect): Boolean {
        if (rect.width() <= 0 || rect.height() <= 0) return false
        val cx = rect.exactCenterX()
        val cy = rect.exactCenterY()
        // Re-resolve against a fresh active root immediately before mutation. Prefer the
        // accessibility action; use a coordinate gesture only when no actionable node
        // resolves or ACTION_CLICK explicitly returns false.
        val root = getActiveRootNode()
        // Chrome's WebView targets often expose a clickable accessibility wrapper that
        // acknowledges ACTION_CLICK without forwarding the tap to the page control.
        // Prefer a real screen gesture there, then retain the node action as fallback.
        if (root?.packageName?.toString() == "com.android.chrome" && clickAt(cx, cy)) return true
        var node = root?.let { findNodeCoveringRect(it, rect) }
        while (node != null) {
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            if (node.isVisibleToUser && node.isClickable && bounds.contains(cx.toInt(), cy.toInt())) {
                if (node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
                break
            }
            node = node.parent
        }
        Log.i(TAG, "Using coordinate fallback at ($cx, $cy) [bounds: $rect]")
        return clickAt(cx, cy)
    }

    fun performSmartScroll(direction: String, distance: String = "small"): Boolean {
        val dm = resources.displayMetrics
        val width = dm.widthPixels.toFloat()
        val height = dm.heightPixels.toFloat()

        // Calculate delta based on distance:
        // "small" = gentle line-by-line scroll (~18% of screen height, moving 1-2 rows at a time with 0 fling)
        // "medium" = ~30% of screen height
        // "large" = ~45% of screen height
        val deltaY = when (distance.lowercase()) {
            "small" -> height * 0.18f
            "large" -> height * 0.45f
            else -> height * 0.28f
        }
        val durationMs = when (distance.lowercase()) {
            "small" -> 600L  // Slow, controlled drag with ZERO fling momentum
            "large" -> 400L
            else -> 550L
        }

        val (startX, startY, endX, endY) = when (direction.lowercase()) {
            "down" -> listOf(width * 0.5f, height * 0.58f, width * 0.5f, height * 0.58f - deltaY)
            "up" -> listOf(width * 0.5f, height * 0.42f, width * 0.5f, height * 0.42f + deltaY)
            "left" -> listOf(width * 0.75f, height * 0.5f, width * 0.25f, height * 0.5f)
            "right" -> listOf(width * 0.25f, height * 0.5f, width * 0.75f, height * 0.5f)
            else -> listOf(width * 0.5f, height * 0.58f, width * 0.5f, height * 0.58f - deltaY)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                val path = Path().apply {
                    moveTo(startX, startY)
                    lineTo(endX, endY)
                }
                val gesture = GestureDescription.Builder()
                    .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
                    .build()
                val dispatched = dispatchGesture(gesture, null, null)
                Log.i(TAG, "Dispatched scroll $direction gesture (distance=$distance, deltaY=$deltaY, duration=${durationMs}ms): $dispatched")
                return dispatched
            } catch (e: Exception) {
                Log.e(TAG, "Scroll gesture failed: ${e.message}")
            }
        }
        return false
    }

    fun findNodeCoveringRect(root: AccessibilityNodeInfo, targetRect: Rect): AccessibilityNodeInfo? {
        val temp = Rect()
        fun search(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
            node.getBoundsInScreen(temp)
            if (Rect.intersects(temp, targetRect)) {
                for (i in (node.childCount - 1) downTo 0) {
                    val child = node.getChild(i) ?: continue
                    val deep = search(child)
                    if (deep != null) return deep
                }
                return node
            }
            return null
        }
        return search(root)
    }

    fun findNodeWithInputFocus(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isFocused) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val res = findNodeWithInputFocus(child)
            if (res != null) return res
        }
        return null
    }

    fun verifyNodeText(node: AccessibilityNodeInfo?, expectedText: String): Boolean {
        if (node == null) return false
        val current = node.text?.toString()?.trim() ?: ""
        val exp = expectedText.trim()
        if (exp.isEmpty()) return true
        return current.contains(exp, ignoreCase = true) ||
               (current.isNotEmpty() && exp.contains(current, ignoreCase = true) && current.length >= 3)
    }

    fun smartTypeText(textToT