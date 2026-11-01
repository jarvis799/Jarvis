package com.jarvis.assistant.model

import android.content.Context
import android.widget.LinearLayout

class OfflineModelHeader(context: Context, back: () -> Unit) : LinearLayout(context) {
    init {
        orientation = VERTICAL
        addView(OfflineModelStyle.button(context, "Back to Settings", back))
        addView(OfflineModelStyle.text(context, "JARVIS / ON-DEVICE INTELLIGENCE", 11f))
        addView(OfflineModelStyle.text(context, "Premium\nOffline AI Models", 30f, true))
        addView(OfflineModelStyle.text(context,
            "Download once. Verify. Auto-setup.\nYour conversations stay on this phone during local inference.", 15f))
        addView(OfflineModelStyle.text(context,
            "Free to use · no premium key or subscription\n${LocalModelRuntime.statusMessage}", 12f))
        addView(OfflineModelStyle.text(context,
            "Recommended: Gemma 4 E2B (effective 2.3B, 5.1B including embeddings). Q4 download: about 3.1 GB; allow space for both the reusable file and a private runtime copy. Free RAM is checked before setup. This integration supports text replies, not Gemma image/audio tools.\n\nTinyLlama and Qwen are lighter alternatives. All recommended sources use Apache-2.0. These are pretrained models, not newly trained Jarvis weights. Offline replies have no live web information.", 12f))
        addView(OfflineModelStyle.text(context,
            "64-bit Android required. Downloads may be large; Wi-Fi is recommended. You can leave this page and resume later. Offline voice also requires a downloaded Android speech/voice pack.", 12f))
    }
}