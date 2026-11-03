package com.jarvis.assistant.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.util.Log

/**
 * HeadsetAudioRouter: Manages audio routing between:
 * 1. External headsets (Bluetooth earbuds / AirPods / wired headsets) -> mic & audio to headset.
 * 2. Device Loudspeaker -> When NO headset is connected, forces playback strictly through
 *    the phone's main loudspeaker (TYPE_BUILTIN_SPEAKER), NEVER the call earpiece speaker.
 */
class HeadsetAudioRouter(private val context: Context) {

    companion object {
        private const val TAG = "HeadsetAudioRouter"
    }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var isRegistered = false
    private var isScoStarted = false
    @Volatile
    private var cachedIsHeadsetConnected = false

    private val headsetReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            Log.d(TAG, "Audio broadcast received: $action")
            when (action) {
                AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED -> {
                    val state = intent.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, AudioManager.SCO_AUDIO_STATE_ERROR)
                    if (state == AudioManager.SCO_AUDIO_STATE_CONNECTED) {
                        Log.i(TAG, "Bluetooth SCO connected: microphone active on Bluetooth headset.")
                    } else if (state == AudioManager.SCO_AUDIO_STATE_DISCONNECTED) {
                        Log.i(TAG, "Bluetooth SCO disconnected.")
                        routeToOptimalAudio()
                    }
                }
                Intent.ACTION_HEADSET_PLUG -> {
                    val state = intent.getIntExtra("state", -1)
                    Log.i(TAG, "Wired headset plug state changed: state=$state")
                    routeToOptimalAudio()
                }
                else -> {
                    routeToOptimalAudio()
                }
            }
        }
    }

    private val audioDeviceCallback = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
                Log.d(TAG, "Audio device added, re-routing audio...")
                routeToOptimalAudio()
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
                Log.d(TAG, "Audio device removed, re-routing audio...")
                routeToOptimalAudio()
            }
        }
    } else null

    fun start() {
        try {
            if (!isRegistered) {
                val filter = IntentFilter().apply {
                    addAction(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED)
                    addAction(Intent.ACTION_HEADSET_PLUG)
                    addAction("android.bluetooth.headset.profile.action.CONNECTION_STATE_CHANGED")
                    addAction("android.bluetooth.adapter.action.STATE_CHANGED")
                }
                // Android 14 rejects a receiver registered without an export flag.
                androidx.core.content.ContextCompat.registerReceiver(
                    context,
                    headsetReceiver,
                    filter,
                    androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
                )

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && audioDeviceCallback != null) {
                    audioManager.registerAudioDeviceCallback(audioDeviceCallback, null)
                }

                isRegistered = true
            }

            routeToOptimalAudio()
        } catch (e: Exception) {
            Log.e(TAG, "Error starting HeadsetAudioRouter: ${e.message}", e)
        }
    }

    /**
     * Checks if an external headset (Bluetooth or wired) is connected.
     * Uses volatile cached state updated on device callbacks to prevent 50Hz IPC overhead.
     */
    fun isHeadsetConnected(): Boolean = cachedIsHeadsetConnected

    /** Any device that plays sound away from the phone speaker (Bluetooth, AirPods, mini box, wired). */
    private fun findExternalOutput(): AudioDeviceInfo? {
        val headsetTypes = intArrayOf(
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_USB_DEVICE,
            AudioDeviceInfo.TYPE_HEARING_AID,
            AudioDeviceInfo.TYPE_BLE_HEADSET,
            AudioDeviceInfo.TYPE_BLE_SPEAKER
        )
        return audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .firstOrNull { device -> headsetTypes.any { it == device.type } }
    }

    /**
     * Playback routing only. Jarvis speaks with USAGE_MEDIA, which Android routes to the
     * connected Bluetooth device or the phone loudspeaker on its own. Forcing
     * MODE_IN_COMMUNICATION plus Bluetooth SCO here pushed that speech onto the call
     * stream, which is silent on most earbuds, AirPods and Bluetooth mini boxes while
     * also muting the phone speaker.
     */
    fun routeToOptimalAudio() {
        try {
            cachedIsHeadsetConnected = findExternalOutput() != null
            releaseCallAudioCapture()
            audioManager.mode = AudioManager.MODE_NORMAL
            audioManager.isSpeakerphoneOn = false
            Log.i(TAG, if (cachedIsHeadsetConnected)
                "External headset connected: media routes to it through the system route."
            else "No external headset: media plays on the phone loudspeaker.")
        } catch (e: Exception) {
            Log.w(TAG, "Failed in routeToOptimalAudio: ${e.message}")
        }
    }

    /** Call audio capturing is never left running once playback starts. */
    private fun releaseCallAudioCapture() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                audioManager.clearCommunicationDevice()
            } catch (_: Exception) {
            }
        }
        if (isScoStarted) {
            try {
                audioManager.stopBluetoothSco()
                audioManager.isBluetoothScoOn = false
            } catch (_: Exception) {
            }
            isScoStarted = false
        }
    }

    /** Truthful picture of the active output, active input and Bluetooth state. */
    fun snapshot(): AudioRouteSnapshot {
        val outputs: Array<AudioDeviceInfo> = runCatching {
            audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        }.getOrDefault(arrayOf())
        val inputs: Array<AudioDeviceInfo> = runCatching {
            audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
        }.getOrDefault(arrayOf())

        val external = findExternalOutput()
        val output = external ?: outputs.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER } ?: outputs.firstOrNull()

        val communication = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching { audioManager.communicationDevice }.getOrNull()
        } else null
        val phoneMic = inputs.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_MIC }
        val scoMic = inputs.firstOrNull { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO }
        val input = if (audioManager.mode == AudioManager.MODE_IN_COMMUNICATION && communication != null) {
            communication
        } else {
            phoneMic ?: scoMic ?: inputs.firstOrNull()
        }

        val inputType = input?.type ?: -1
        return AudioRouteSnapshot(
            bluetoothConnected = external != null,
            bluetoothName = bluetoothDeviceName(),
            outputName = output?.productName?.toString()?.takeIf { it.isNotBlank() } ?: "no output device",
            outputKind = audioKindLabel(output?.type ?: -1),
            inputName = input?.productName?.toString()?.takeIf { it.isNotBlank() } ?: "no input device",
            inputKind = audioKindLabel(inputType),
            bluetoothMic = inputType == AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            micAvailable = input != null
        )
    }

    private fun audioKindLabel(type: Int): String = when (type) {
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Phone speaker"
        AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "Phone earpiece"
        AudioDeviceInfo.TYPE_BUILTIN_MIC -> "Phone microphone"
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth audio (A2DP)"
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth headset (SCO)"
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "Wired headphones"
        AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Wired headset"
        AudioDeviceInfo.TYPE_USB_HEADSET -> "USB headset"
        AudioDeviceInfo.TYPE_USB_DEVICE -> "USB audio device"
        AudioDeviceInfo.TYPE_HEARING_AID -> "Hearing aid"
        AudioDeviceInfo.TYPE_BLE_HEADSET -> "Bluetooth LE headset"
        AudioDeviceInfo.TYPE_BLE_SPEAKER -> "Bluetooth LE speaker"
        else -> "Audio device"
    }

    /** Name of the connected Bluetooth device, when the owner allowed Bluetooth control. */
    private fun bluetoothDeviceName(): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && androidx.core.content.ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.BLUETOOTH_CONNECT
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) return ""
        return runCatching {
            val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager
            manager.adapter?.getBondedDevices()?.firstOrNull { it.isConnected }?.name.orEmpty()
        }.getOrDefault("")
    }

    fun stop() {
        try {
            if (isRegistered) {
                try {
                    context.unregisterReceiver(headsetReceiver)
                } catch (_: Exception) {}

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && audioDeviceCallback != null) {
                    try {
                        audioManager.unregisterAudioDeviceCallback(audioDeviceCallback)
                    } catch (_: Exception) {}
                }

                isRegistered = false
            }

            releaseCallAudioCapture()
            audioManager.mode = AudioManager.MODE_NORMAL
            audioManager.isSpeakerphoneOn = false
            cachedIsHeadsetConnected = false
            Log.i(TAG, "HeadsetAudioRouter stopped, audio mode reset to NORMAL media routing.")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping HeadsetAudioRouter: ${e.message}", e)
        }
    }
}