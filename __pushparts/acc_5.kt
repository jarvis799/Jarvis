ndSubmitButtonInTree(child)
                if (found != null) return found
            }
            return null
        }

        val isMatch = desc.contains("send") || desc.contains("submit") || desc.contains("generate") ||
                desc.contains("create image") || desc.contains("create video") ||
                desc.contains("run") || desc.contains("done") || desc.contains("apply") ||
                desc.contains("go") || desc.contains("search") || desc.contains("enter") ||
                desc == "→" || desc == "▶" || desc.contains("arrow") ||
                text == "send" || text == "go" || text == "generate" || text == "create" ||
                text == "submit" || text == "done" || text == "search" || text == "run" ||
                vid.contains("send_button") || vid.contains("send_btn") ||
                vid.contains("search_button") || vid.contains("search_go_btn") ||
                vid.contains("submit_button") || vid.contains("generate_button") ||
                vid.contains("action_button") || vid.contains("btn_send")

        if (isMatch && (node.isClickable || node.parent?.isClickable == true)) {
            return node
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findSubmitButtonInTree(child)
            if (found != null) return found
        }
        return null
    }

    /**
     * Position-based fallback: finds the send/generate button by its typical screen position
     * (small clickable button in the bottom-right area). Used when Gemini's arrow button has
     * no text or contentDescription that matches keyword search.
     */
    private fun findSendButtonByPosition(): AccessibilityNodeInfo? {
        val root = getAppRootNode() ?: getActiveRootNode() ?: return null
        val displayMetrics = resources.displayMetrics
        val screenW = displayMetrics.widthPixels
        val screenH = displayMetrics.heightPixels

        // Bottom-right quadrant: right 35% width, bottom 25% height
        val searchLeft = (screenW * 0.65f).toInt()
        val searchTop = (screenH * 0.75f).toInt()

        val candidates = mutableListOf<Pair<AccessibilityNodeInfo, Rect>>()

        fun collectCandidates(node: AccessibilityNodeInfo) {
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            if (node.isClickable && !node.isEditable &&
                bounds.left >= searchLeft && bounds.top >= searchTop &&
                bounds.width() in 30..300 && bounds.height() in 30..200) {
                val desc = node.contentDescription?.toString()?.lowercase() ?: ""
                val vid = node.viewIdResourceName?.lowercase() ?: ""
                // Exclude attach/voice/mic buttons
                val isExcluded = desc.contains("attach") || desc.contains("mic") ||
                        desc.contains("voice") || desc.contains("emoji") ||
                        desc.contains("camera") || desc.contains("sticker") ||
                        vid.contains("mic") || vid.contains("voice") || vid.contains("attach")
                if (!isExcluded) {
                    candidates.add(Pair(node, bounds))
                }
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { collectCandidates(it) }
            }
        }
        collectCandidates(root)

        // Pick the rightmost button (send/generate is always at far right)
        return candidates.maxByOrNull { it.second.right }?.first
    }

    fun performSystemGesture(gesture: String): Boolean {
        return when (gesture.lowercase()) {
            "home" -> performGlobalAction(GLOBAL_ACTION_HOME)
            "back" -> performGlobalAction(GLOBAL_ACTION_BACK)
            "recents" -> performGlobalAction(GLOBAL_ACTION_RECENTS)
            "notifications" -> performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
            else -> false
        }
    }
}
