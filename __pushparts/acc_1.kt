ck send!
        // When entry is empty, the button is guaranteed to be a microphone/voice note button.
        val textAfterInject = getWhatsAppEntryText(rootNode)
        if (textAfterInject.isNullOrBlank()) {
            Log.d(TAG, "Entry field is empty; skipping send click to prevent clicking mic (attempt $attempt)")
            return false
        }

        // 2. Find and click ONLY the actual Send button (never send_container which holds the mic)
        var clicked = false
        val sendButtonIds = listOf(
            "com.whatsapp:id/send",
            "com.whatsapp.w4b:id/send",
            "com.whatsapp:id/send_btn",
            "com.whatsapp:id/send_media_button",
            "com.whatsapp.w4b:id/send_btn"
        )
        for (id in sendButtonIds) {
            val nodes = rootNode.findAccessibilityNodeInfosByViewId(id)
            for (node in nodes) {
                if (isVoiceOrMicNode(node)) {
                    Log.d(TAG, "Ignoring voice/mic node with ID $id")
                    continue
                }
                if (clickNodeAndTap(node)) {
                    Log.i(TAG, "Successfully clicked WhatsApp send button by ID: $id (attempt $attempt)")
                    clicked = true
                    whatsAppSendTaps++
                    break
                }
            }
            if (clicked) break
        }

        if (!clicked) {
            clicked = findAndClickSendInTree(rootNode)
            if (clicked) whatsAppSendTaps++
        }

        if (clicked) {
            // Schedule an immediate verification check
            handler.postDelayed({
                val checkRoot = getActiveRootNode()
                if (checkRoot != null && getWhatsAppEntryText(checkRoot).isNullOrBlank()) {
                    Log.i(TAG, "Verified: WhatsApp entry field is clear. Disarming send.")
                    isWaitingForWhatsAppSend = false
                    pendingWhatsAppMessage = null
                }
            }, 300)
        }

        return clicked
    }

    private fun isVoiceOrMicNode(node: AccessibilityNodeInfo): Boolean {
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""
        val viewId = node.viewIdResourceName?.lowercase() ?: ""
        val className = node.className?.toString()?.lowercase() ?: ""

        if (desc.contains("voice") || desc.contains("record") || desc.contains("audio") ||
            desc.contains("mic") || desc.contains("microphone") || desc.contains("ptt") ||
            desc.contains("hold to") || desc.contains("ध्वनि") || desc.contains("बोलकर")) {
            return true
        }
        if (viewId.contains("voice") || viewId.contains("record") || viewId.contains("mic") || viewId.contains("ptt")) {
            return true
        }
        // Inspect child elements
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val cDesc = child.contentDescription?.toString()?.lowercase() ?: ""
            val cId = child.viewIdResourceName?.lowercase() ?: ""
            if (cDesc.contains("voice") || cDesc.contains("record") || cDesc.contains("audio") ||
                cDesc.contains("mic") || cDesc.contains("ptt") || cId.contains("mic") || cId.contains("voice")) {
                return true
            }
        }
        return false
    }

    private fun getWhatsAppEntryText(root: AccessibilityNodeInfo): String? {
        val entryIds = listOf("com.whatsapp:id/entry", "com.whatsapp.w4b:id/entry")
        for (id in entryIds) {
            val nodes = root.findAccessibilityNodeInfosByViewId(id)
            for (node in nodes) {
                val text = node.text?.toString()
                if (text != null) return text.trim()
            }
        }
        // Fallback: search EditText
        return findFirstEditTextContent(root)
    }

    private fun findFirstEditTextContent(node: AccessibilityNodeInfo): String? {
        if (node.className?.toString()?.contains("EditText") == true) {
            return node.text?.toString()?.trim()
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findFirstEditTextContent(child)
            if (result != null) return result
        }
        return null
    }

    private fun injectTextIfEmpty(root: AccessibilityNodeInfo) {
        val targetText = pendingWhatsAppMessage
        if (targetText.isNullOrBlank()) return

        val entryIds = listOf(
            "com.whatsapp:id/entry",
            "com.whatsapp.w4b:id/entry"
        )
        for (id in entryIds) {
            val nodes = root.findAccessibilityNodeInfosByViewId(id)
            for (node in nodes) {
                val currentText = node.text?.toString()?.trim() ?: ""
                if (currentText.isBlank()) {
                    val args = Bundle().apply {
                        putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, targetText)
                    }
                    val set = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                    Log.i(TAG, "Injected text into WhatsApp entry field: success=$set")
                    return
                }
            }
        }

        // Fallback: check all EditTexts in the active tree
        findAndInjectEditText(root, targetText)
    }

    private fun findAndInjectEditText(node: AccessibilityNodeInfo, text: String): Boolean {
        if (node.className?.toString()?.contains("EditText") == true) {
            val currentText = node.text?.toString()?.trim() ?: ""
            if (currentText.isBlank()) {
                val args = Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
                }
                val set = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                Log.i(TAG, "Injected text into generic EditText: success=$set")
                return true
            }
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (findAndInjectEditText(child, text)) return true
        }
        return false
    }

    private fun findAndClickSendInTree(node: AccessibilityNodeInfo): Boolean {
        if (isVoiceOrMicNode(node)) return false

        val contentDesc = node.contentDescription?.toString()?.lowercase() ?: ""
        val viewId = node.viewIdResourceName?.lowercase() ?: ""

        // If content description is send
        if (contentDesc == "send" || contentDesc == "send message" || contentDesc == "enviar" || contentDesc == "भेजें") {
            if (clickNodeAndTap(node)) return true
        }

        // Strictly match send button IDs (never containers)
        if (viewId.endsWith("/send") || viewId.endsWith("/send_btn") || viewId.endsWith("/send_button") || viewId.endsWith("/send_media_button")) {
            if (clickNodeAndTap(node)) return true
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (findAndClickSendInTree(child)) return true
        }

        return false
    }

    // -------------------------------------------------------------
    // WhatsApp Voice & Video Calling
    // -------------------------------------------------------------

    fun armWhatsAppCall(video: Boolean) {
        isWaitingForWhatsAppCall = true
        isVideoCall = video
        whatsAppCallArmTime = System.currentTimeMillis()
        whatsAppCallTaps = 0
        Log.i(TAG, "Armed for WhatsApp auto-call (video=$video)")
        handler.postDelayed({ pollWhatsAppCall(1) }, 700)
    }

    private fun pollWhatsAppCall(attempt: Int) {
        if (!isWaitingForWhatsAppCall || attempt > 16) {
            isWaitingForWhatsAppCall = false
            return
        }

        val clicked = attemptWhatsAppCallAction(attempt)
        if (clicked && whatsAppCallTaps >= 2) {
            isWaitingForWhatsAppCall = false
            return
        }

        val delay = if (attempt < 4) 400L else 700L
        handler.postDelayed({ pollWhatsAppCall(attempt + 1) }, delay)
    }

    private fun attemptWhatsAppCallAction(attempt: Int): Boolean {
        val rootNode = getActiveRootNode() ?: return false

        var clicked = false

        // 1. Check for system/app confirmation dialog ("Start voice call?", "Call", etc.)
        val dialogButtons = listOf("android:id/button1", "com.whatsapp:id/ok", "com.whatsapp:id/call_button")
        for (btnId in dialogButtons) {
            val nodes = rootNode.findAccessibilityNodeInfosByViewId(btnId)
            for (node in nodes) {
                if (clickNodeAndTap(node)) {
                    Log.i(TAG, "Clicked call confirmation dialog button: $btnId")
                    whatsAppCallTaps++
                    clicked = true
                    break
                }
            }
            if (clicked) break
        }

        // Also check confirmation dialog text
        if (!clicked) {
            val callConfirmNodes = rootNode.findAccessibilityNodeInfosByText("Call")
            for (node in callConfirmNodes) {
                if (node.isClickable || node.parent?.isClickable == true) {
                    if (clickNodeAndTap(node)) {
                        Log.i(TAG, "Clicked confirmation dialog button by text 'Call'")
                        whatsAppCallTaps++
                        clicked = true
                        break
                    }
                }
            }
        }

        // 2. Click Top Action Bar Call Button (Video or Voice)
        val targetIds = if (isVideoCall) {
            listOf(
                "com.whatsapp:id/video_call",
                "com.whatsapp.w4b:id/video_call",
                "com.whatsapp:id/menuitem_conversations_call_video"
            )
        } else {
            listOf(
                "com.whatsapp:id/voice_call",
                "com.whatsapp.w4b:id/voice_call",
                "com.whatsapp:id/menuitem_conversations_call_voice"
            )
        }

        for (id in targetIds) {
            val nodes = rootNode.findAccessibilityNodeInfosByViewId(id)
            for (node in nodes) {
                if (clickNodeAndTap(node)) {
                    Log.i(TAG, "Clicked WhatsApp call button by ID: $id (video=$isVideoCall)")
                    whatsAppCallTaps++
                    clicked = true
                    break
                }
            }
            if (clicked) break
        }

        // Fallback by content description in tree
        if (!clicked) {
            clicked = findAndClickCallButtonInTree(rootNode, isVideoCall)
            if (clicked) whatsAppCallTaps++
        }

        return clicked
    }

    private fun findAndClickCallButtonInTree(node: AccessibilityNodeInfo, isVideo: Boolean): Boolean {
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""
        val targetPhrase = if (isVideo) "video call" else "voice call"

        if (desc.contains(targetPhrase) || (!isVideo && (desc == "call" || desc.contains("audio call")))) {
            if (clickNodeAndTap(node)) {
                Log.i(TAG, "Clicked call button by content description: $desc")
                return true
            }
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (findAndClickCallButtonInTree(child, isVideo)) return true
        }

        return false
    }

    // -------------------------------------------------------------
    // YouTube Auto Play
    // -------------------------------------------------------------

    fun armYouTubePlay() {
        isWaitingForYouTubePlay = true
        youTubeArmTime = System.currentTimeMillis()
        Log.i(TAG, "Armed for YouTube auto-play")
        handler.postDelayed({ pollYouTubePlay(1) }, 1000)
    }

    private fun pollYouTubePlay(attempt: Int) {
        if (!isWaitingForYouTubePlay || attempt > 10) {
            isWaitingForYouTubePlay = false
            return
        }
        val clicked = attemptClickFirstYouTubeVideo(attempt)
        if (!clicked) {
            handler.postDelayed({ pollYouTubePlay(attempt + 1) }, 600)
        }
    }

    private fun attemptClickFirstYouTubeVideo(attempt: Int): Boolean {
        val rootNode = rootInActiveWindow ?: return false

        val videoViewIds = listOf(
            "com.google.android.youtube:id/thumbnail",
            "com.google.android.youtube:id/video_info",
            "com.google.android.youtube:id/title",
            "com.google.android.youtube:id/compact_renderer",
            "app.revanced.android.youtube:id/thumbnail",
            "app.revanced.android.youtube:id/title"
        )

        for (id in videoViewIds) {
            val nodes = rootNode.findAccessibilityNodeInfosByViewId(id)
            for (node in nodes) {
                if (clickNodeAndTap(node)) {
                    Log.i(TAG, "Clicked first YouTube video item by ID: $id (attempt $attempt)")
                    isWaitingForYouTubePlay = false
                    return true
                }
            }
        }

        return false
    }

    // -------------------------------------------------------------
    // YouTube Subscribe
    // -------------------------------------------------------------

    fun armYouTubeSubscribe() {
        isWaitingForYouTubeSubscribe = true
        youTubeSubscribeArmTime = System.currentTimeMillis()
        Log.i(TAG, "Armed for YouTube subscribe")
        handler.postDelayed({ pollYouTubeSubscribe(1) }, 500)
    }

    private fun pollYouTubeSubscribe(attempt: Int) {
        if (!isWaitingForYouTubeSubscribe || attempt > 12) {
            isWaitingForYouTubeSubscribe = false
            return
        }
        if (!attemptYouTubeSubscribe()) {
            handler.postDelayed({ pollYouTubeSubscribe(attempt + 1) }, 500)
        }
    }

    private fun attemptYouTubeSubscribe(): Boolean {
        val rootNode = rootInActiveWindow ?: return false

        val subIds = listOf(
            "com.google.android.youtube:id/subscribe_button",
            "app.revanced.android.youtube:id/subscribe_button"
        )
        for (id in subIds) {
            val nodes = rootNode.findAccessibilityNodeInfosByViewId(id)
            for (node in nodes) {
                val text = node.text?.toString()?.lowercase() ?: ""
                val desc = node.contentDescription?.toString()?.lowercase() ?: ""
                if (text.contains("subscribed") || desc.contains("subscribed")) {
                    Log.i(TAG, "Already subscribed to this channel; nothing to do.")
                    isWaitingForYouTubeSubscribe = false
                    return true
                }
                if (clickNodeAndTap(node)) {
                    Log.i(TAG, "Clicked YouTube subscribe button by ID: $id")
                    isWaitingForYouTubeSubscribe = false
                    return true
                }
            }
        }

        // Fallback: find any clickable node whose text/description says "Subscribe"
        // (but never one that already reads "Subscribed", to avoid accidentally unsubscribing).
        val fallback = findSubscribeNodeInTree(rootNode)
        if (fallback != null && clickNodeAndTap(fallback)) {
            Log.i(TAG, "Clicked YouTube subscribe button via tree fallback")
            isWaitingForYouTubeSubscribe = false
            return true
        }

        return false
    }

    private fun findSubscribeNodeInTree(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val text = node.text?.toString()?.lowercase() ?: ""
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""
        val looksSubscribed = text.contains("subscribed") || desc.contains("subscribed")
        val looksLikeSubscribeButton = (text == "subscribe" || desc == "subscribe") && !looksSubsc