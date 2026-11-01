package com.jarvis.assistant.lock

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class VoiceUnlockStoreTest {
    private lateinit var context: Context
    @Before fun reset() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("jarvis_voice_unlock", Context.MODE_PRIVATE).edit().clear().commit()
    }
    @Test fun customPhrasePersistsAndMatchesWithoutPunctuationOrCase() {
        VoiceUnlockStore(context).save(null, "Open my Jarvis")
        assertTrue(VoiceUnlockStore(context).matches("OPEN, my Jarvis!"))
        assertFalse(VoiceUnlockStore(context).matches("open my other app"))
    }
    @Test fun editingAndDeletingRemovesTheOldSample() {
        val store = VoiceUnlockStore(context)
        store.save(null, "open jarvis"); store.sample("open jarvis").writeText("sample")
        store.save("open jarvis", "hello jarvis")
        assertFalse(store.matches("open jarvis")); assertFalse(store.sample("open jarvis").exists())
        store.delete("hello jarvis"); assertTrue(store.phrases().isEmpty())
    }
    @Test fun unicodeMarksAndPhraseBoundariesArePreserved() {
        val store = VoiceUnlockStore(context); store.save(null, "আমার ফোন খোলো")
        assertTrue(store.matches("আমার ফোন খোলো!")); assertFalse(store.matches("আমার ফোন"))
    }
    @Test(expected = IllegalArgumentException::class) fun duplicatePhraseIsRejected() {
        val store = VoiceUnlockStore(context); store.save(null, "Open Jarvis"); store.save(null, "open jarvis!")
    }
    @Test fun preferencesAreExplicitlyOptIn() {
        val store = VoiceUnlockStore(context); assertFalse(store.lockEnabled()); assertFalse(store.webEnabled())
        store.setLockEnabled(true); assertTrue(VoiceUnlockStore(context).lockEnabled())
    }
}