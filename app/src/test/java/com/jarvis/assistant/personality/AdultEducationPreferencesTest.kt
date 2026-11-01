package com.jarvis.assistant.personality

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.jarvis.assistant.data.model.GeminiConstants
import com.jarvis.assistant.data.preferences.AppPreferences
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class, sdk = [28])
class AdultEducationPreferencesTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    @Before fun clear() {
        context.getSharedPreferences("jarvis_prefs", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun freshInstallIsDisabled() {
        assertFalse(AppPreferences(context).adultEducationEnabled)
    }

    @Test fun bothStatesSurviveReopeningThePreferences() {
        AppPreferences(context).adultEducationEnabled = true
        assertTrue(AppPreferences(context).adultEducationEnabled)
        AppPreferences(context).adultEducationEnabled = false
        assertFalse(AppPreferences(context).adultEducationEnabled)
    }

    @Test fun educationAndGirlfriendPersonaRemainIndependent() {
        val prefs = AppPreferences(context)
        prefs.personality = GeminiConstants.PERSONALITY_GIRLFRIEND
        val voice = prefs.voice
        prefs.adultEducationEnabled = true
        assertEquals(GeminiConstants.PERSONALITY_GIRLFRIEND, prefs.personality)
        prefs.personality = GeminiConstants.PERSONALITY_ASSISTANT
        assertTrue(AppPreferences(context).adultEducationEnabled)
        prefs.adultEducationEnabled = false
        assertEquals(GeminiConstants.PERSONALITY_ASSISTANT, prefs.personality)
        assertEquals(voice, prefs.voice)
    }
}