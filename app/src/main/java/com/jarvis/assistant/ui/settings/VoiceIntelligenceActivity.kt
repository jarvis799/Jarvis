package com.jarvis.assistant.ui.settings

import android.os.Bundle
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import com.jarvis.assistant.databinding.ActivityVoiceIntelligenceBinding
import com.jarvis.assistant.util.ThemeManager
import com.jarvis.assistant.voice.ListeningProfile
import com.jarvis.assistant.voice.Utterance
import com.jarvis.assistant.voice.UtteranceRoute
import com.jarvis.assistant.voice.UtteranceRouter
import com.jarvis.assistant.voice.VoiceIntelligencePrefs

/**
 * Voice Intelligence: the listening profiles, the noise and interruption controls, and a
 * live view of how the local classifier reads a sentence.
 *
 * Every control here is read by real code paths (the wake-word gate uses the profile and the
 * sensitivity multiplier), so nothing on this screen is decorative.
 */
class VoiceIntelligenceActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVoiceIntelligenceBinding
    private lateinit var prefs: VoiceIntelligencePrefs
    private val router = UtteranceRouter { prefs.profile }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyTheme(this)
        super.onCreate(savedInstanceState)
        binding = ActivityVoiceIntelligenceBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ThemeManager.applyDynamicBackground(binding.root)
        prefs = VoiceIntelligencePrefs(this)

        binding.btnBack.setOnClickListener { finish() }
        binding.btnQuiet.setOnClickListener { selectProfile(ListeningProfile.QUIET) }
        binding.btnNormal.setOnClickListener { selectProfile(ListeningProfile.NORMAL) }
        binding.btnPublic.setOnClickListener { selectProfile(ListeningProfile.PUBLIC_PLACE) }

        binding.seekWake.setOnSeekBarChangeListener(slider { prefs.wakeSensitivity = it; render() })
        binding.seekNoise.setOnSeekBarChangeListener(slider { prefs.noiseSuppression = it; render() })
        binding.seekInterrupt.setOnSeekBarChangeListener(slider { prefs.interruptionSensitivity = it; render() })

        binding.btnClassify.setOnClickListener { classifyTypedUtterance() }

        render()
    }

    private fun slider(onChange: (Int) -> Unit) = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
            if (fromUser) onChange(progress)
        }
        override fun onStartTrackingTouch(bar: SeekBar?) {}
        override fun onStopTrackingTouch(bar: SeekBar?) {}
    }

    private fun selectProfile(profile: ListeningProfile) {
        prefs.profile = profile
        render()
    }

    private fun render() {
        val profile = prefs.profile
        binding.btnQuiet.text = label(ListeningProfile.QUIET, profile)
        binding.btnNormal.text = label(ListeningProfile.NORMAL, profile)
        binding.btnPublic.text = label(ListeningProfile.PUBLIC_PLACE, profile)
        binding.tvProfileDescription.text = profile.description

        binding.seekWake.progress = prefs.wakeSensitivity
        binding.seekNoise.progress = prefs.noiseSuppression
        binding.seekInterrupt.progress = prefs.interruptionSensitivity

        binding.tvWakeValue.text = "Wake-word sensitivity: " + prefs.wakeSensitivity + "%"
        binding.tvNoiseValue.text = "Noise suppression: " + prefs.noiseSuppression + "%"
        binding.tvInterruptValue.text = "Interruption sensitivity: " + prefs.interruptionSensitivity + "%"

        binding.tvVerdict.text = ""
        binding.tvVerdictDetail.text = "The threshold the wake gate uses right now is " +
            String.format(java.util.Locale.US, "%.2f", prefs.wakeConfidenceFactor()) +
            "x the base value (" + profile.label + ")."
    }

    private fun label(option: ListeningProfile, current: ListeningProfile): String =
        if (option == current) "\u2713  " + option.label else option.label

    private fun classifyTypedUtterance() {
        val text = binding.editUtterance.text?.toString().orEmpty()
        if (text.isBlank()) {
            binding.tvVerdict.text = "Type a sentence first"
            binding.tvVerdictDetail.text = "Try \"open YouTube\", \"why is the sky blue\", or \"no, open Chrome\"."
            return
        }
        val decision = router.decide(Utterance(text = text, fromWake = true))
        binding.tvVerdict.text = routeLabel(decision.route) + "  \u2022  " + decision.classification.category.name
        binding.tvVerdictDetail.text = decision.reason +
            "\nConfidence: " + String.format(java.util.Locale.US, "%.2f", decision.classification.confidence) +
            (if (decision.classification.actionCount > 0) "\nActions detected: " + decision.classification.actionCount else "")
    }

    private fun routeLabel(route: UtteranceRoute): String = when (route) {
        UtteranceRoute.LOCAL_COMMAND -> "Handled on the phone"
        UtteranceRoute.CONVERSATION -> "Sent to the AI"
        UtteranceRoute.CLARIFY -> "Ask first"
        UtteranceRoute.IGNORE_AMBIENT -> "Ignored as background"
        UtteranceRoute.ACCEPT_CORRECTION -> "Treated as a correction"
    }
}
