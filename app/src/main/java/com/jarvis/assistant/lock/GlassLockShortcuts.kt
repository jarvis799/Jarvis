package com.jarvis.assistant.lock

import android.Manifest
import android.app.Activity
import android.app.KeyguardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.provider.MediaStore
import androidx.core.content.ContextCompat

class GlassLockShortcuts(private val activity: Activity, private val status: (String) -> Unit) {
    private var torch: String? = null
    private var pending = ""
    fun use(action: String) {
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            pending = action; activity.requestPermissions(arrayOf(Manifest.permission.CAMERA), 804); return
        }
        try {
            if (action == "camera") {
                val locked = activity.getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true
                activity.startActivity(Intent(if (locked) MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE else MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))
            } else {
                val manager = activity.getSystemService(CameraManager::class.java)
                val camera = manager.cameraIdList.firstOrNull {
                    manager.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                } ?: error("No flashlight on this device.")
                if (torch == null) { manager.setTorchMode(camera, true); torch = camera; status("Flashlight on") }
                else { close(); status("Flashlight off") }
            }
        } catch (error: Exception) { status(error.message ?: "Camera or flashlight is unavailable.") }
    }
    fun permission(granted: Boolean) {
        if (granted) use(pending) else status("Camera permission denied. Use Android app permissions to allow camera and flashlight.")
    }
    fun close() {
        torch?.let { id -> runCatching { activity.getSystemService(CameraManager::class.java).setTorchMode(id, false) } }
        torch = null
    }
}