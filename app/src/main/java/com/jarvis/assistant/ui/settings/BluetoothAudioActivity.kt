package com.jarvis.assistant.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.ToneGenerator
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.jarvis.assistant.audio.AudioRoutePrefs
import com.jarvis.assistant.audio.HeadsetAudioRouter
import com.jarvis.assistant.databinding.ActivityBluetoothAudioBinding
import com.jarvis.assistant.util.ThemeManager
import kotlin.math.abs

/**
 * Bluetooth audio: real device names, real input/output selection and a real
 * microphone capture test. Nothing on this screen is a mock.
 */
class BluetoothAudioActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBluetoothAudioBinding
    private lateinit var router: HeadsetAudioRouter
    private val main = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyTheme(this)
        super.onCreate(savedInstanceState)
        binding = ActivityBluetoothAudioBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ThemeManager.applyDynamicBackground(binding.root)
        router = HeadsetAudioRouter(this)

        binding.btnBack.setOnClickListener { finish() }

        binding.swAutoRouting.isChecked = AudioRoutePrefs.autoRouting(this)
        binding.swPreferOutput.isChecked = AudioRoutePrefs.preferBluetoothOutput(this)
        binding.swPreferFallback.isChecked = AudioRoutePrefs.preferPhoneMicFallback(this)

        binding.swAutoRouting.setOnCheckedChangeListener { _, checked ->
            AudioRoutePrefs.setAutoRouting(this, checked)
            if (checked) router.routeToOptimalAudio()
            render()
        }
        binding.swPreferOutput.setOnCheckedChangeListener { _, checked ->
            AudioRoutePrefs.setPreferBluetoothOutput(this, checked)
            router.routeToOptimalAudio()
            render()
        }
        binding.swPreferFallback.setOnCheckedChangeListener { _, checked ->
            AudioRoutePrefs.setPreferPhoneMicFallback(this, checked)
            render()
        }

        binding.btnTestMic.setOnClickListener { testMicrophone() }
        binding.btnTestSpeaker.setOnClickListener { testSpeaker() }
        binding.btnReset.setOnClickListener {
            AudioRoutePrefs.setAutoRouting(this, true)
            AudioRoutePrefs.setPreferBluetoothOutput(this, true)
            AudioRoutePrefs.setPreferPhoneMicFallback(this, true)
            binding.swAutoRouting.isChecked = true
            binding.swPreferOutput.isChecked = true
            binding.swPreferFallback.isChecked = true
            router.routeToOptimalAudio()
            render()
            toast("Audio routing reset to automatic")
        }
    }

    override fun onResume() {
        super.onResume()
        router.start()
        render()
    }

    override fun onDestroy() {
        router.stop()
        super.onDestroy()
    }

    private fun render() {
        val route = router.snapshot()
        binding.tvConnectionBadge.text = route.connectionLabel
        binding.tvDeviceName.text = if (route.bluetoothConnected)
            route.bluetoothName.ifBlank { "Bluetooth audio device connected" }
        else
            "No Bluetooth earbuds or headphones connected"
        binding.tvOutput.text = route.outputKind + " • " + route.outputName
        binding.tvInput.text = route.inputKind + " • " + route.inputName
        binding.tvMicAvailable.text = when {
            !route.micAvailable -> "Microphone unavailable right now"
            route.bluetoothMic -> "Bluetooth microphone is the active input"
            else -> "Phone microphone is the active input; Bluetooth keeps the media output"
        }
    }

    private fun testSpeaker() {
        val route = router.snapshot()
        runCatching {
            val tone = ToneGenerator(AudioManager.STREAM_MUSIC, 85)
            tone.startTone(ToneGenerator.TONE_PROP_BEEP2, 800)
            main.postDelayed({ runCatching { tone.release() } }, 1100)
        }.onFailure { toast("This device cannot play the test tone") }
        toast("Playing a short tone through " + route.outputKind)
    }

    private fun testMicrophone() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), MIC_REQUEST)
            return
        }
        val input = router.snapshot().inputKind
        binding.tvMicResult.text = "Capturing two seconds from " + input + "…"

        Thread {
            var recorder: AudioRecord? = null
            var peak = 0
            try {
                val rate = 16000
                val min = AudioRecord.getMinBufferSize(
                    rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
                )
                recorder = AudioRecord(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    rate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    if (min > 0) min else rate
                )
                recorder.startRecording()
                val buffer = ShortArray(1600)
                val until = System.currentTimeMillis() + 2000L
                while (System.currentTimeMillis() < until) {
                    val read = recorder.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        for (index in 0 until read) {
                            val value = abs(buffer[index].toInt())
                            if (value > peak) peak = value
                        }
                    }
                }
            } catch (_: Exception) {
            } finally {
                runCatching { recorder?.stop() }
                runCatching { recorder?.release() }
            }
            val captured = peak
            main.post {
                binding.tvMicResult.text = if (captured > 200)
                    "Microphone works (" + input + ") — peak level " + captured
                else
                    "No sound reached the microphone. Check the permission and that the headset is not muted."
            }
        }.start()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == MIC_REQUEST) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                testMicrophone()
            } else {
                toast("Microphone permission is required for the test")
            }
        }
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val MIC_REQUEST = 4812
    }
}
