ype: String, targetRect: Rect? = null, submit: Boolean = false): Boolean {
        lastTypeVerified = false
        if (textToType.isBlank()) {
            Log.w(TAG, "smartTypeText refused blank input")
            return false
        }
        Log.i(TAG, "smartTypeText starting (length=${textToType.length}, targeted=${targetRect != null}, submit=$submit)")

        if (targetRect != null && targetRect.width() > 0 && targetRect.height() > 0) {
            if (!performClickAtRect(targetRect)) return false
            Thread.sleep(100)
        }

        val appRoot = getAppRootNode() ?: getActiveRootNode() ?: return false
        val inBrowser = (appRoot.packageName?.toString() ?: "").let {
            it.contains("chrome", ignoreCase = true) || it.contains("browser", ignoreCase = true)
        }

        fun editableAt(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
            if (targetRect != null && targetRect.width() > 0) {
                var node = findNodeCoveringRect(root, targetRect)
                while (node != null) {
                    if (node.isEditable || node.className?.toString()?.contains("EditText", true) == true) return node
                    node = node.parent
                }
                return null
            }
            return root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
                ?: findNodeWithInputFocus(root)
                ?: findFirstEditableNode(root)
        }

        var target = editableAt(appRoot)
        if (target != null && isUrlBarNode(target)) {
            Log.w(TAG, "smartTypeText refused browser URL/address bar")
            return false
        }
        if (target == null) {
            Log.w(TAG, "smartTypeText found no verified editable target; no text was entered")
            return false
        }

        fun currentTarget(): AccessibilityNodeInfo? {
            val freshRoot = getActiveRootNode() ?: getAppRootNode() ?: return null
            return editableAt(freshRoot)
        }
        fun textEqualsExpected(node: AccessibilityNodeInfo?): Boolean {
            val current = node?.text?.toString()?.replace("\r\n", "\n")?.trim().orEmpty()
            val expected = textToType.replace("\r\n", "\n").trim()
            return current == expected
        }
        fun setValue(node: AccessibilityNodeInfo, value: String): Boolean {
            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value)
            }
            return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        }

        // Repeated model tool calls become idempotent: matching text is success, never paste it again.
        if (textEqualsExpected(target)) {
            lastTypeVerified = true
            Log.i(TAG, "smartTypeText skipped duplicate request: the exact text is already present")
        } else {
            var setAccepted = false
            try {
                setAccepted = setValue(target, textToType)
            } catch (e: Exception) {
                Log.w(TAG, "ACTION_SET_TEXT failed: ${e.message}")
            }

            if (setAccepted) {
                Thread.sleep(180)
                target = currentTarget()
                lastTypeVerified = textEqualsExpected(target)
                if (!lastTypeVerified) {
                    // A successful SET_TEXT with no readable confirmation may already have changed
                    // the UI. Do not paste on top of it; that was the source of repeated text.
                    Log.w(TAG, "Text set was not read back exactly; refusing append-style fallback")
                    return false
                }
            } else {
                // Paste fallback is safe only after the current field has been verified empty.
                // Clearing is done with ACTION_SET_TEXT, never by tapping an ambiguous clear icon.
                val fresh = currentTarget() ?: target
                val currentText = fresh.text?.toString().orEmpty()
                if (currentText.isNotBlank()) {
                    val cleared = try { setValue(fresh, "") } catch (_: Exception) { false }
                    Thread.sleep(120)
                    target = currentTarget()
                    if (!cleared || !target?.text?.toString().isNullOrBlank()) {
                        Log.w(TAG, "Existing input could not be safely cleared; refusing to append text")
                        return false
                    }
                } else {
                    target = fresh
                }

                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                try {
                    clipboard?.setPrimaryClip(ClipData.newPlainText("JarvisInput", textToType))
                    val pasteTarget = currentTarget() ?: target ?: return false
                    if (isUrlBarNode(pasteTarget)) return false
                    val pasted = pasteTarget.performAction(AccessibilityNodeInfo.ACTION_PASTE) ||
                        pasteTarget.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_PASTE.id)
                    if (!pasted) {
                        Log.w(TAG, "Safe paste unavailable; no second typing strategy was attempted")
                        return false
                    }
                    Thread.sleep(180)
                    lastTypeVerified = textEqualsExpected(currentTarget())
                    if (!lastTypeVerified) {
                        Log.w(TAG, "Pasted text could not be verified exactly; refusing to retry and duplicate it")
                        return false
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Safe paste failed: ${e.message}")
                    return false
                }
            }
        }

        // Never auto-tap Send/Submit as a side effect of typing. Search/submit actions
        // must be a separate, freshly inspected click; the service enforces send intent.
        if (submit) Log.i(TAG, "Submit requested after typing; waiting for an explicit grounded submit action")
        return lastTypeVerified
    }

    private fun performLongPressAndPaste(targetRect: Rect): Boolean {
        val clickX = targetRect.centerX().toFloat()
        val clickY = targetRect.centerY().toFloat()
        val path = Path().apply { moveTo(clickX, clickY) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 450))
            .build()
        try {
            dispatchGesture(gesture, null, null)
        } catch (e: Exception) {
            Log.e(TAG, "Long-press gesture dispatch failed: ${e.message}")
            return false
        }

        // Active polling: wait for Android floating text selection toolbar to appear (up to 800ms)
        val startTime = System.currentTimeMillis()
        var pasteNode: AccessibilityNodeInfo? = null
        while (System.currentTimeMillis() - startTime < 800L) {
            try { Thread.sleep(60) } catch (_: Exception) {}
            pasteNode = findPasteButtonAcrossWindows()
            if (pasteNode != null) break
        }

        if (pasteNode != null) {
            Log.i(TAG, "Found native Paste popup menu item: ${pasteNode.text ?: pasteNode.contentDescription}")
            val clicked = clickNodeAndTap(pasteNode)
            Thread.sleep(120)
            return clicked
        }
        return false
    }

    private fun findPasteButtonAcrossWindows(): AccessibilityNodeInfo? {
        val pasteKeywords = listOf("paste", "चिपकाएं", "पेस्ट", "paste from clipboard", "insert")
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                for (window in windows) {
                    val root = window.root ?: continue
                    val found = searchNodeForKeywords(root, pasteKeywords)
                    if (found != null) return found
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Error inspecting windows for paste button: ${e.message}")
        }
        val appRoot = getAppRootNode() ?: getActiveRootNode() ?: return null
        return searchNodeForKeywords(appRoot, pasteKeywords)
    }

    private fun searchNodeForKeywords(node: AccessibilityNodeInfo, keywords: List<String>): AccessibilityNodeInfo? {
        val text = node.text?.toString()?.lowercase(Locale.ROOT) ?: ""
        val desc = node.contentDescription?.toString()?.lowercase(Locale.ROOT) ?: ""
        val vid = node.viewIdResourceName?.lowercase(Locale.ROOT) ?: ""

        for (kw in keywords) {
            if (text == kw || desc == kw || text.contains(kw) || desc.contains(kw) || vid.contains(kw)) {
                if (node.isClickable || node.parent?.isClickable == true) {
                    return if (node.isClickable) node else node.parent
                }
            }
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = searchNodeForKeywords(child, keywords)
            if (found != null) return found
        }
        return null
    }

    /**
     * Searches across ALL active windows — specifically including TYPE_INPUT_METHOD (Gboard) —
     * for a clipboard suggestion chip or paste action button.
     */
    private fun findClipboardChipAcrossAllWindows(textToType: String): AccessibilityNodeInfo? {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                // Priority 1: Check Input Method window (Gboard/keyboard strip)
                for (window in windows) {
                    if (window.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD) {
                        val root = window.root ?: continue
                        val found = findClipboardSuggestionChip(root, textToType)
                        if (found != null) {
                            Log.i(TAG, "Found clipboard suggestion chip in TYPE_INPUT_METHOD window!")
                            return found
                        }
                    }
                }
                // NOTE: other windows (Chrome page, menus, omnibox suggestions) are intentionally NOT searched:
                // matching generic "chip"/"suggestion" ids there caused random mis-taps.
            }
        } catch (e: Exception) {
            Log.d(TAG, "Error searching windows for clipboard chip: ${e.message}")
        }
        return null
    }

    private fun findClipboardSuggestionChip(node: AccessibilityNodeInfo, textToType: String): AccessibilityNodeInfo? {
        val snippet = textToType.take(8).lowercase(Locale.ROOT)
        val text = node.text?.toString()?.lowercase(Locale.ROOT) ?: ""
        val desc = node.contentDescription?.toString()?.lowercase(Locale.ROOT) ?: ""
        val vid = node.viewIdResourceName?.lowercase(Locale.ROOT) ?: ""

        val isChipMatch = (vid.contains("clipboard") || desc.contains("clipboard") ||
            (snippet.length >= 3 && (text.contains(snippet) || desc.contains(snippet))))

        if (isChipMatch && (node.isClickable || node.parent?.isClickable == true)) {
            return if (node.isClickable) node else node.parent
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findClipboardSuggestionChip(child, textToType)
            if (found != null) return found
        }
        return null
    }

    /** True for Chrome's address bar / omnibox / NTP search box. Page prompts must NEVER be typed here. */
    fun isUrlBarNode(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        val vid = node.viewIdResourceName?.lowercase(Locale.ROOT) ?: ""
        return vid.contains("url_bar") || vid.contains("omnibox") ||
               vid.contains("location_bar") || vid.contains("search_box_text")
    }

    /** True while the soft keyboard (an input-method window) is on screen. */
    fun isKeyboardVisible(): Boolean {
        return try {
            windows.any { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }
        } catch (e: Exception) {
            false
        }
    }

    /** Result of the last smartTypeText call: true only if the typed text was READ BACK from the screen. */
    @Volatile
    var lastTypeVerified: Boolean = false

    /**
     * Reads the real screen (application windows only; NOT the keyboard, so the Gboard clipboard chip
     * cannot cause a false positive; NOT the URL bar) and checks whether the start of [text] is visible.
     * Polls for up to ~1.2s because Chrome syncs web inputs to the accessibility tree with a delay.
     */
    fun verifyTextVisibleAnywhere(text: String): Boolean {
        val snippet = text.trim().take(24).lowercase(Locale.ROOT)
        if (snippet.length < 3) return true
        fun scan(n: AccessibilityNodeInfo, depth: Int): Boolean {
            if (depth > 60) return false
            if (!isUrlBarNode(n)) {
                val t = n.text?.toString()?.lowercase(Locale.ROOT) ?: ""
                val d = n.contentDescription?.toString()?.lowercase(Locale.ROOT) ?: ""
                if (t.contains(snippet) || d.contains(snippet)) return true
            }
            for (i in 0 until n.childCount) {
                val c = n.getChild(i) ?: continue
                if (scan(c, depth + 1)) return true
            }
            return false
        }
        repeat(4) {
            try {
                for (w in windows) {
                    if (w.type != AccessibilityWindowInfo.TYPE_APPLICATION) continue
                    val r = w.root ?: continue
                    if (scan(r, 0)) return true
                }
            } catch (e: Exception) {
                Log.d(TAG, "verifyTextVisibleAnywhere error: ${e.message}")
            }
            try { Thread.sleep(300) } catch (_: Exception) {}
        }
        return false
    }

    fun findFirstEditableNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if ((node.isEditable || node.className?.toString()?.contains("EditText") == true) && !isUrlBarNode(node)) {
            return node
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val res = findFirstEditableNode(child)
            if (res != null) return res
        }
        return null
    }

    private fun findSearchOrSubmitButton(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        return findSubmitButtonInTree(node) ?: findSendButtonByPosition()
    }

    /** Depth-first search for a named send/generate/submit button, explicitly excluding attach/add/+ buttons. */
    private fun findSubmitButtonInTree(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""
        val text = node.text?.toString()?.lowercase() ?: ""
        val vid = node.viewIdResourceName?.lowercase() ?: ""

        // EXPLICITLY EXCLUDE attachment / add-media / plus buttons — these are NOT submit buttons
        val isAttachOrAddButton = desc.contains("attach") || desc.contains("add file") ||
                desc.contains("add media") || desc.contains("add image") ||
                desc.contains("add attachment") || desc.contains("add photo") ||
                desc.contains("upload") || desc.contains("insert") ||
                text == "+" || text == "add" ||
                vid.contains("attach") || vid.contains("add_media") ||
                vid.contains("add_file") || vid.contains("add_photo") ||
                vid.contains("media_button") || vid.contains("plus_button")
        if (isAttachOrAddButton) {
            // Don't search this node but DO search its siblings via parent — skip to children check
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                val found = fi