package com.jarvis.assistant.util

import android.Manifest
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, manifest = Config.NONE)
class VoiceStartPermissionTest {
    @Test fun optionalSettingsCannotBlockAMicrophonePermission() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO)
        shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        assertFalse(PermissionHelper.hasNotificationPermission(app))
        assertFalse(PermissionHelper.hasBatteryOptimizationPermission(app))
        assertTrue(PermissionHelper.canStartVoiceSession(app))
    }

    @Test fun deniedMicrophoneStillBlocksVoice() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app).denyPermissions(Manifest.permission.RECORD_AUDIO)
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        assertFalse(PermissionHelper.canStartVoiceSession(app))
    }
}
