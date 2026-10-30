package com.jarvis.assistant.ui.features

import android.app.Activity
import com.jarvis.assistant.email.InboxActivity
import com.jarvis.assistant.model.ModelCatalogueActivity
import com.jarvis.assistant.personality.MemoryManagementActivity
import com.jarvis.assistant.personality.PersonalityStudioActivity
import com.jarvis.assistant.screen.ScreenActionsActivity
import com.jarvis.assistant.ui.offline.OfflineRepliesActivity
import com.jarvis.assistant.ui.settings.DeveloperProfileActivity
import com.jarvis.assistant.ui.settings.OrbDensityActivity
import com.jarvis.assistant.ui.settings.VoiceStudioActivity

data class FeatureLink(val title: String, val description: String, val target: Class<out Activity>)

object FeatureCatalog {
    val items = listOf(
        FeatureLink("Email Inbox", "Read messages and send replies; connect in Gmail Settings", InboxActivity::class.java),
        FeatureLink("Offline Replies", "Answer without internet from the on-device engine", OfflineRepliesActivity::class.java),
        FeatureLink("Local AI Models", "Search and download GGUF models; offline inference needs the native engine", ModelCatalogueActivity::class.java),
        FeatureLink("Voice Studio", "Preview device TTS voices and save pitch / speed styles", VoiceStudioActivity::class.java),
        FeatureLink("Orb Density", "Choose how many particles the orb draws", OrbDensityActivity::class.java),
        FeatureLink("Screen Actions", "Read the current screen and tap its items", ScreenActionsActivity::class.java),
        FeatureLink("Personality Studio", "Humor, personality and custom instructions", PersonalityStudioActivity::class.java),
        FeatureLink("Memory Management", "Review saved memory and conversation settings", MemoryManagementActivity::class.java),
        FeatureLink("Developer Info", "Developer identity and official profile", DeveloperProfileActivity::class.java)
    )

    fun matching(query: String): List<FeatureLink> = items.filter {
        query.isBlank() || (it.title + " " + it.description).contains(query.trim(), ignoreCase = true)
    }
}
