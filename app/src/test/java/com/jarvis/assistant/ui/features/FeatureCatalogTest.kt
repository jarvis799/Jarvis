package com.jarvis.assistant.ui.features

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FeatureCatalogTest {
    @Test fun exposesEveryNewFeatureDirectly() {
        assertEquals(
            listOf("Email Inbox", "Offline Replies", "Local AI Models", "Voice Studio", "Orb Density", "Screen Actions", "Personality Studio", "Memory Management", "Developer Info"),
            FeatureCatalog.items.map { it.title }
        )
        assertEquals(FeatureCatalog.items.size, FeatureCatalog.items.map { it.target }.toSet().size)
    }

    @Test fun searchesTitlesAndDescriptionsWithoutCaseSensitivity() {
        assertEquals("Voice Studio", FeatureCatalog.matching("  tTs  ").single().title)
        assertEquals("Email Inbox", FeatureCatalog.matching("send replies").single().title)
        assertTrue(FeatureCatalog.matching("not-a-feature").isEmpty())
    }

    @Test fun emptySearchRestoresEveryFeature() {
        assertEquals(FeatureCatalog.items, FeatureCatalog.matching("   "))
    }
}
