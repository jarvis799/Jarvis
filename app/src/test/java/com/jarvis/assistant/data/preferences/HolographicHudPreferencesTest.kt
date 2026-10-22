package com.jarvis.assistant.data.preferences

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class, sdk = [28])
class HolographicHudPreferencesTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val storage get() = context.getSharedPreferences("jarvis_prefs", Context.MODE_PRIVATE)

    @Before fun clearPreferences() { storage.edit().clear().commit() }

    @Test fun freshInstallSelectsTheActualHudPage() {
        val preferences = AppPreferences(context)
        assertEquals("hud", preferences.appTheme)
        assertEquals(AppPreferences.ORB_HUD, preferences.orbStyle)
    }

    @Test fun upgradeFromOldLightMigrationActivatesHudWithoutClearingUserData() {
        storage.edit().putBoolean("liquid_glass_ui_v2", true)
            .putString("key_app_theme", "light").putString("key_orb_style", "jarvis1")
            .putString("key_user_name", "Saved owner").putString("key_chat_history", "[]").commit()
        val preferences = AppPreferences(context)
        preferences.activateLiquidGlassUi()
        assertEquals("hud", preferences.appTheme)
        assertEquals(AppPreferences.ORB_HUD, preferences.orbStyle)
        assertEquals("Saved owner", storage.getString("key_user_name", null))
        assertEquals("[]", storage.getString("key_chat_history", null))
        assertTrue(storage.getBoolean("holographic_hud_ui_v3", false))
    }

    @Test fun laterThemeAndOrbChoicesAreNotOverriddenOnNextLaunch() {
        val preferences = AppPreferences(context)
        preferences.activateLiquidGlassUi()
        preferences.appTheme = "red"
        preferences.orbStyle = AppPreferences.ORB_FACE
        val reopened = AppPreferences(context)
        reopened.activateLiquidGlassUi()
        assertEquals("red", reopened.appTheme)
        assertEquals(AppPreferences.ORB_FACE, reopened.orbStyle)
    }

    @Test fun storedHudKeyDoesNotFallBackToLight() {
        storage.edit().putString("key_app_theme", "hud").commit()
        assertEquals("hud", AppPreferences(context).appTheme)
    }
}
