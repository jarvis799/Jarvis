package com.jarvis.assistant.ui.onboarding

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.jarvis.assistant.JarvisApp
import com.jarvis.assistant.R
import com.jarvis.assistant.databinding.ActivityGeminiOnboardingBinding
import com.jarvis.assistant.ui.settings.ApiConnectionActivity

/**
 * First-run Gemini key setup.
 *
 * The key is written through [AppPreferences.apiKey], which stores it in
 * EncryptedSharedPreferences; nothing here logs or echoes the key value.
 */
class GeminiOnboardingActivity : AppCompatActivity() {

    companion object {
        private const val GEMINI_KEYS_URL = "https://aistudio.google.com/app/apikey"
    }

    private lateinit var binding: ActivityGeminiOnboardingBinding
    private val preferences by lazy { JarvisApp.instance.preferences }
    private var keyVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGeminiOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.progressOne.setBackgroundResource(R.drawable.bg_onb_progress_active)
        binding.progressTwo.setBackgroundResource(R.drawable.bg_onb_progress_idle)
        binding.progressThree.setBackgroundResource(R.drawable.bg_onb_progress_idle)

        if (preferences.apiKey.isNotBlank()) {
            binding.etApiKey.setText(preferences.apiKey)
        }
        applyKeyVisibility(false)

        binding.btnGetFreeKey.setOnClickListener { openKeyPortal() }
        binding.btnPasteKey.setOnClickListener { pasteFromClipboard() }
        binding.tvToggleKey.setOnClickListener { applyKeyVisibility(!keyVisible) }
        binding.btnSaveContinue.setOnClickListener { saveKeyAndContinue() }
        binding.tvHaveKey.setOnClickListener { openExistingKeys() }
        binding.tvManageKeys.setOnClickListener { openExistingKeys() }
        binding.tvSkip.setOnClickListener { finishOnboarding() }
    }

    override fun onResume() {
        super.onResume()
        if (::binding.isInitialized && binding.etApiKey.text.isNullOrBlank() && preferences.apiKey.isNotBlank()) {
            binding.etApiKey.setText(preferences.apiKey)
        }
    }

    private fun applyKeyVisibility(visible: Boolean) {
        keyVisible = visible
        val cursor = binding.etApiKey.selectionEnd.coerceAtLeast(0)
        binding.etApiKey.inputType = if (visible) {
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        } else {
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        binding.etApiKey.setSelection(cursor.coerceAtMost(binding.etApiKey.text?.length ?: 0))
        binding.tvToggleKey.text = if (visible) "HIDE" else "SHOW"
    }

    private fun openKeyPortal() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(GEMINI_KEYS_URL)))
        } catch (e: Exception) {
            Toast.makeText(this, "No browser is available to open Google AI Studio.", Toast.LENGTH_LONG).show()
        }
    }

    private fun pasteFromClipboard() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val pasted = clipboard.primaryClip
            ?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)
            ?.coerceToText(this)
            ?.toString()
            ?.trim()
            .orEmpty()
        if (pasted.isEmpty()) {
            Toast.makeText(this, "Clipboard is empty. Copy your Gemini API key first.", Toast.LENGTH_LONG).show()
            return
        }
        binding.etApiKey.setText(pasted)
        binding.etApiKey.setSelection(pasted.length)
        Toast.makeText(this, "Key pasted from the clipboard.", Toast.LENGTH_SHORT).show()
    }

    private fun saveKeyAndContinue() {
        val key = binding.etApiKey.text?.toString()?.trim().orEmpty()
        if (key.isEmpty()) {
            Toast.makeText(this, "Enter your Gemini API key first, or choose Skip for now.", Toast.LENGTH_LONG).show()
            binding.etApiKey.requestFocus()
            return
        }
        if (!preferences.canStoreApiKeySecurely) {
            Toast.makeText(
                this,
                "Encrypted storage is unavailable on this device, so the key cannot be saved.",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        try {
            preferences.apiKey = key
        } catch (e: Exception) {
            Toast.makeText(this, "Could not save the key to encrypted storage.", Toast.LENGTH_LONG).show()
            return
        }
        com.jarvis.assistant.background.JarvisConversationService.restartSession()
        Toast.makeText(this, "Gemini key saved securely on this device.", Toast.LENGTH_SHORT).show()
        preferences.geminiOnboardingCompleted = true
        finish()
    }

    private fun openExistingKeys() {
        startActivity(Intent(this, ApiConnectionActivity::class.java))
    }

    private fun finishOnboarding() {
        preferences.geminiOnboardingCompleted = true
        Toast.makeText(this, "You can add your Gemini key any time from Settings.", Toast.LENGTH_SHORT).show()
        finish()
    }
}