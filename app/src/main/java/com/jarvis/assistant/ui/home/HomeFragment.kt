package com.jarvis.assistant.ui.home

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.jarvis.assistant.JarvisApp
import com.jarvis.assistant.data.model.ConversationState
import com.jarvis.assistant.databinding.FragmentHomeBinding
import com.jarvis.assistant.ui.memory.MemoryActivity
import com.jarvis.assistant.util.LocationWeatherHelper
import com.jarvis.assistant.util.PermissionHelper
import android.content.res.ColorStateList
import androidx.core.content.ContextCompat
import com.jarvis.assistant.R
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MainViewModel get() = (requireActivity() as MainActivity).viewModel
    private val preferences by lazy { JarvisApp.instance.preferences }

    private var isPageLoaded = false
    private var loadedOrbStyle: String? = null
    private var pendingOrbState: String? = null
    private var pendingThemeKey: String? = null
    private var pendingCustomHex: String? = null
    private var lastAudioPushTime = 0L
    private var taskFallbackPanel: android.widget.LinearLayout? = null
    private var taskFallbackText: android.widget.TextView? = null
    private var hudSurface: HudHomeSurface? = null

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (fineGranted || coarseGranted) {
            fetchLiveWeather()
        } else {
            binding.tvWeatherCondition.text = "Location Permission Needed"
            binding.tvWeatherDetails.text = "Tap here to grant location for accurate weather"
        }
    }

    // Small home-screen status: free minutes left today, or the premium badge text.
    private fun updateFreeUsageLabel() {
        val b = _binding ?: return
        val remaining = com.jarvis.assistant.backend.FreeUsageManager.remainingMs(requireContext())
        if (remaining == Long.MAX_VALUE) {
            b.tvFreeUsage.text = "FREE • Unlimited for everyone"
            b.tvFreeUsage.setTextColor(android.graphics.Color.parseColor("#B2B2B2"))
        } else {
            val mins = (remaining / 60000L).coerceAtLeast(0)
            b.tvFreeUsage.text = "FREE • Unlimited for everyone"
            b.tvFreeUsage.setTextColor(android.graphics.Color.parseColor("#727272"))
        }
    }


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        com.jarvis.assistant.util.ThemeManager.applyDynamicBackground(binding.root)
        hudSurface = HudHomeSurface(binding)
        createTaskFallbackPanel()
        com.jarvis.assistant.email.GmailEntryPoint.bind(binding.btnGmailSetup.root as android.widget.TextView)

        // Top Navigation
        binding.btnTopChat.setOnClickListener {
            (activity as? MainActivity)?.selectTab(MainActivity.TAB_CHAT)
        }

        binding.btnTopSettings.setOnClickListener {
            (activity as? MainActivity)?.selectTab(MainActivity.TAB_SETTINGS)
        }

        binding.btnDismissLiveError.setOnClickListener {
            hideLiveErrorOverlay {
                viewModel.clearLiveError()
            }
        }

        setupOrbWebView()

        // Free-usage / premium status chip (refreshes every 30s while the view is alive)
        val usageTicker = android.os.Handler(android.os.Looper.getMainLooper())
        val usageRunnable = object : Runnable {
            override fun run() {
                if (_binding == null) return
                updateFreeUsageLabel()
                usageTicker.postDelayed(this, 30_000L)
            }
        }
        usageTicker.post(usageRunnable)

        // Action Buttons
        binding.btnScreenShareQuick.setOnClickListener {
            (activity as? MainActivity)?.toggleLiveScreenShare()
        }

        binding.btnSavedMemories.setOnClickListener {
            startActivity(Intent(requireContext(), MemoryActivity::class.java))
        }

        binding.btnConversationLanguageQuick.setOnClickListener {
            showLanguageDialog()
        }

        // Weather Card Click -> Refresh or Request Permission
        binding.cardWeather.setOnClickListener {
            if (!PermissionHelper.hasLocationPermission(requireContext())) {
                locationPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            } else {
                fetchLiveWeather()
            }
        }

        updateHomeHeaderAndVoiceStatus()
        observeViewModel()
        checkLocationAndFetchWeather()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupOrbWebView() {
        binding.orbWebView.apply {
            setBackgroundColor(Color.TRANSPARENT)
            setLayerType(View.LAYER_TYPE_HARDWARE, null)

            // DEV STYLE: HUD hologram bridge - its header buttons talk through window.JarvisBridge
            addJavascriptInterface(object {
                @android.webkit.JavascriptInterface
                fun onMicClicked() { post { (activity as? MainActivity)?.handleMicOrOrbClick() } }
                @android.webkit.JavascriptInterface
                fun onOrbClicked() { post { (activity as? MainActivity)?.handleMicOrOrbClick() } }
                @android.webkit.JavascriptInterface
                fun onPowerClicked() { post {
                    if (viewModel.isSessionOn.value) (activity as? MainActivity)?.handleHoldMicOrOrb()
                    else (activity as? MainActivity)?.handleMicOrOrbClick()
                } }
                @android.webkit.JavascriptInterface
                fun onSettingsClicked() {
                    post { com.jarvis.assistant.JarvisApp.instance.startActivity(android.content.Intent(com.jarvis.assistant.JarvisApp.instance, com.jarvis.assistant.ui.settings.SettingsActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }
                @android.webkit.JavascriptInterface
                fun onHistoryClicked() { post { (activity as? MainActivity)?.selectTab(MainActivity.TAB_CHAT) } }
                @android.webkit.JavascriptInterface
                fun onScreenShareClicked() { post { (activity as? MainActivity)?.toggleLiveScreenShare() } }
            }, "JarvisBridge")

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = true
                allowContentAccess = true
                @Suppress("DEPRECATION")
                allowFileAccessFromFileURLs = true
                @Suppress("DEPRECATION")
                allowUniversalAccessFromFileURLs = true
                cacheMode = WebSettings.LOAD_NO_CACHE
            }

            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    isPageLoaded = true

                    // Sync Theme
                    val currentTheme = pendingThemeKey ?: preferences.appTheme
                    val customHex = pendingCustomHex ?: preferences.customThemeColor
                    setOrbTheme(currentTheme, customHex)

                    // Sync State
                    val state = pendingOrbState ?: when (viewModel.conversationState.value) {
                        ConversationState.LISTENING -> "listening"
                        ConversationState.SPEAKING -> "speaking"
                        ConversationState.THINKING -> "thinking"
                        ConversationState.IDLE -> "idle"
                    }
                    setOrbState(state)
                }
            }

            // Single tap detection triggers mic/conversation without interrupting 3D rotation drag
            val gestureDetector = GestureDetector(requireContext(), object : GestureDetector.SimpleOnGestureListener() {
                override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                    (activity as? MainActivity)?.handleMicOrOrbClick()
                    return true
                }

                override fun onLongPress(e: MotionEvent) {
                    (activity as? MainActivity)?.handleHoldMicOrOrb()
                }
            })

            setOnTouchListener { v, event ->
                if (loadedOrbStyle == "hud-hologram") false
                else { gestureDetector.onTouchEvent(event); v.onTouchEvent(event) }
            }
            loadedOrbStyle = preferences.orbStyle
            hudSurface?.apply(loadedOrbStyle == "hud-hologram")
            loadUrl(orbUrl(loadedOrbStyle ?: "jarvis1"))
        }
    }

    private fun syncOrbStyle() {
        val style = preferences.orbStyle
        hudSurface?.apply(style == "hud-hologram")
        if (loadedOrbStyle != null && loadedOrbStyle != style) {
            // The HUD hologram lives on its own page - switching to or from it needs a reload.
            if (style == "hud-hologram" || loadedOrbStyle == "hud-hologram") {
                isPageLoaded = false
                loadedOrbStyle = style
                binding.orbWebView.loadUrl(orbUrl(style))
                return
            }
            binding.orbWebView.evaluateJavascript("window.JarvisVisual && window.JarvisVisual.set('" + style + "')", null)
        }
        loadedOrbStyle = style
    }

    fun setOrbState(state: String) {
        pendingOrbState = state
        if (!isPageLoaded) return
        activity?.runOnUiThread {
            binding.orbWebView.evaluateJavascript("window.JarvisOrb && window.JarvisOrb.setState('$state');", null)
        }
    }

    fun setOrbTheme(themeKey: String, customHex: String?) {
        pendingThemeKey = themeKey
        pendingCustomHex = customHex
        if (!isPageLoaded) return
        activity?.runOnUiThread {
            val key = if (themeKey == "custom") "custom" else themeKey
            binding.orbWebView.evaluateJavascript("window.JarvisOrb && window.JarvisOrb.setTheme('$key');", null)
            if (key == "custom" && !customHex.isNullOrBlank()) {
                binding.orbWebView.evaluateJavascript("window.JarvisOrb && window.JarvisOrb.setBackground('$customHex');", null)
            }
        }
    }
    private fun orbUrl(style: String): String {
        if (style == "hud-hologram") return "file:///android_asset/hud/index.html?home=1"
        val theme = preferences.appTheme
        val accent = if (theme == "custom") preferences.customThemeColor else ""
        val url = StringBuilder("file:///android_asset/orbs/orb-host.html?embedded=1&theme=").append(theme)
        if (accent.isNotBlank()) url.append("&accent=").append(android.net.Uri.encode(accent))
        return url.append("&orb=").append(style).toString()
    }

    private val orbSayHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var lastOrbSayTime = 0L
    private var pendingOrbText: String? = null
    private val orbSayFlush = object : Runnable {
        override fun run() {
            val t = pendingOrbText ?: return
            pendingOrbText = null
            pushOrbSay(t)
        }
    }

    /** Shows Jarvis's own answer on the orb. Throttled to ~9 updates/sec so streaming
     *  text chunks don't flood the main thread and freeze the WebView (black flash). */
    fun sayToOrb(text: String) {
        val clean = text.trim()
        if (clean.isEmpty() || !isPageLoaded) return
        val now = android.os.SystemClock.uptimeMillis()
        if (now - lastOrbSayTime >= 110L) {
            lastOrbSayTime = now
            orbSayHandler.removeCallbacks(orbSayFlush)
            pendingOrbText = null
            pushOrbSay(clean)
        } else {
            pendingOrbText = clean
            orbSayHandler.removeCallbacks(orbSayFlush)
            orbSayHandler.postDelayed(orbSayFlush, 110L)
        }
    }

    private fun pushOrbSay(clean: String) {
        if (_binding == null) return
        val escaped = clean.replace("\\", "\\\\").replace("'", "\\'").replace("\n", " ").replace("\r", "")
        activity?.runOnUiThread {
            if (_binding == null) return@runOnUiThread
            binding.orbWebView.evaluateJavascript("window.JarvisOrb && window.JarvisOrb.say('$escaped');", null)
        }
    }

    private fun pushExternalAudio(amp: Float, bass: Float, high: Float) {
        if (!isPageLoaded) return
        val now = System.currentTimeMillis()
        if (now - lastAudioPushTime < 33L) return // ~30 calls per second throttle
        lastAudioPushTime = now
        activity?.runOnUiThread {
            binding.orbWebView.evaluateJavascript("window.JarvisOrb && window.JarvisOrb.setExternalAudio($amp, $bass, $high);", null)
        }
    }

    override fun onResume() {
        super.onResume()
        com.jarvis.assistant.util.ThemeManager.applyDynamicBackground(binding.root)
        binding.orbWebView.onResume()
        binding.orbWebView.resumeTimers()
        com.jarvis.assistant.email.GmailEntryPoint.bind(binding.btnGmailSetup.root as android.widget.TextView)
        renderTaskFallback(com.jarvis.assistant.automation.agent.AgentTaskEngine.taskState.value)

        // Sync theme with settings
        setOrbTheme(preferences.appTheme, preferences.customThemeColor)
        syncOrbStyle()
        updateHomeHeaderAndVoiceStatus()

        if (PermissionHelper.hasLocationPermission(requireContext())) {
            fetchLiveWeather()
        }
    }

    override fun onPause() {
        super.onPause()
        binding.orbWebView.onPause()
        binding.orbWebView.pauseTimers()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden && _binding != null) {
            binding.root.visibility = View.VISIBLE
            binding.orbWebView.visibility = View.VISIBLE
            binding.orbWebView.onResume()
            binding.orbWebView.resumeTimers()
            com.jarvis.assistant.util.ThemeManager.applyDynamicBackground(binding.root)
            setOrbTheme(preferences.appTheme, preferences.customThemeColor)
        syncOrbStyle()
            updateHomeHeaderAndVoiceStatus()
        } else if (hidden && _binding != null) {
            binding.orbWebView.onPause()
            binding.orbWebView.pauseTimers()
            binding.orbWebView.visibility = View.GONE
            binding.root.visibility = View.GONE
        }
    }

    private fun checkLocationAndFetchWeather() {
        if (PermissionHelper.hasLocationPermission(requireContext())) {
            fetchLiveWeather()
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun fetchLiveWeather() {
        binding.tvWeatherCondition.text = "Locating • Fetching weather..."
        viewLifecycleOwner.lifecycleScope.launch {
            val weather = LocationWeatherHelper.fetchWeather(requireContext())
            if (_binding != null && weather != null) {
                binding.tvTemperature.text = weather.temperature
                binding.tvWeatherCondition.text = weather.condition
                binding.tvWeatherDetails.text = weather.details
                binding.ivWeatherIcon.setImageResource(weather.iconResId)
                // Keep Jarvis's context aligned with the city shown on Home.
                preferences.userLocation = weather.cityName
            } else if (_binding != null) {
                binding.tvWeatherCondition.text = "Live Weather • Tap to retry"
            }
        }
    }

    private fun updateHomeHeaderAndVoiceStatus() {
        if (_binding == null) return
        val userName = preferences.userName.ifBlank { "Arian" }
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val timeGreeting = when (hour) {
            in 4..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            in 17..21 -> "Good evening"
            else -> "Welcome back"
        }
        binding.tvGreeting.text = "$timeGreeting, $userName"

        val currentLang = preferences.language.ifBlank { "Auto" }
        binding.tvQuickLanguageStatus.text = if (currentLang.contains("Auto", ignoreCase = true)) "Auto" else currentLang
    }

    private fun showLanguageDialog() {
        val languages = com.jarvis.assistant.data.model.GeminiConstants.SUPPORTED_LANGUAGES.toTypedArray()
        val currentLang = preferences.language
        val currentIndex = languages.indexOfFirst { it.equals(currentLang, ignoreCase = true) }.let { if (it >= 0) it else 0 }

        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle("Select Conversation Language")
            .setSingleChoiceItems(languages, currentIndex) { dialog, which ->
                val chosen = languages[which]
                preferences.language = chosen
                binding.tvQuickLanguageStatus.text = chosen
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    com.jarvis.assistant.automation.agent.AgentTaskEngine.taskState.collect { snapshot ->
                        renderTaskFallback(snapshot)
                    }
                }
                launch {
                    val screenCapture = com.jarvis.assistant.vision.ScreenCaptureManager.getInstance()
                    combine(screenCapture.isSharing, screenCapture.hasReceivedFrame) { sharing, hasFrame -> sharing to hasFrame }
                        .collect { (sharing, hasFrame) ->
                        binding.tvQuickScreenShareStatus.text = when {
                            !sharing -> "Share screen"
                            !hasFrame -> "Starting…"
                            else -> "Stop sharing"
                        }
                        binding.tvQuickScreenShareStatus.setTextColor(
                            if (sharing && hasFrame) Color.parseColor("#808080")
                            else ContextCompat.getColor(requireContext(), R.color.text_subtle)
                        )
                    }
                }
                launch {
                    val chatRepository = (requireContext().applicationContext as com.jarvis.assistant.JarvisApp).chatRepository
                    var spokenTurnId: String? = chatRepository.turns.value.lastOrNull()?.id
                    chatRepository.turns.collect { turns ->
                        val latest = turns.lastOrNull() ?: return@collect
                        if (latest.id == spokenTurnId) return@collect
                        spokenTurnId = latest.id
                        sayToOrb(latest.jarvisResponse)
                    }
                }
                // Live transcript: stream Jarvis's reply onto the orb as it is
                // generated (not only the finalized turn), and show what the boss
                // just said on the hint line while listening/processing.
                launch {
                    viewModel.liveAssistantResponse.collect { reply ->
                        if (reply.isNotBlank()) sayToOrb(reply)
                    }
                }
                launch {
                    viewModel.liveUserTranscript.collect { you ->
                        if (_binding == null) return@collect
                        val state = viewModel.conversationState.value
                        if (you.isNotBlank() && (state == ConversationState.LISTENING || state == ConversationState.THINKING)) {
                            binding.tvHoldHint.text = "You: $you"
                            binding.tvHoldHint.visibility = View.VISIBLE
                        }
                    }
                }
                launch {
                    combine(
                        viewModel.isSessionOn,
                        viewModel.isMicMuted,
                        viewModel.conversationState,
                        com.jarvis.assistant.background.JarvisConversationService.isStandby,
                        combine(viewModel.connectionStatus, com.jarvis.assistant.background.JarvisConversationService.wakeDetail) { connection, detail -> connection to detail }
                    ) { isOn, isMuted, state, isStandby, connection ->
                        HomeStatusState(isOn, isMuted, state, isStandby, connection.first, connection.second)
                    }.collect { status ->
                        binding.orbWebView.evaluateJavascript("window.setPowerState && window.setPowerState(${status.isOn || status.isStandby}); window.setMicMuted && window.setMicMuted(${status.isMuted});", null)
                        if (!status.isOn && !status.isStandby) {
                            setOrbState("idle")
                            binding.tvListeningStatus.text = "JARVIS OFF • TAP TO START"
                            binding.tvListeningStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_subtle))
                            binding.tvHoldHint.text = "Tap mic or orb to power on"
                            binding.tvHoldHint.visibility = View.VISIBLE
                            binding.dotStatusGlow.backgroundTintList = ColorStateList.valueOf(
                                Color.parseColor("#727272")
                            )
                        } else if (status.isMuted && !status.isStandby) {
                            setOrbState("idle")
                            binding.tvListeningStatus.text = "MIC MUTED (OFF) • TAP TO TALK"
                            binding.tvListeningStatus.setTextColor(Color.parseColor("#777777"))
                            binding.tvHoldHint.text = "Microphone is muted • Tap mic to unmute"
                            binding.tvHoldHint.visibility = View.VISIBLE
                            binding.dotStatusGlow.backgroundTintList = ColorStateList.valueOf(
                                Color.parseColor("#777777")
                            )
                        } else if (status.isStandby) {
                            setOrbState("idle")
                            binding.tvListeningStatus.text = when (status.connection) {
                                "WAKE_LISTENING" -> "STANDBY • SAY 'HEY JARVIS'"
                                "WAKE_LOADING" -> "LOADING WAKE MODEL..."
                                "WAKE_RETRYING" -> "WAKE MICROPHONE • RETRYING"
                                else -> "CHECKING WAKE MICROPHONE..."
                            }
                            binding.tvListeningStatus.setTextColor(Color.parseColor("#7A7A7A"))
                            binding.tvHoldHint.text = status.wakeDetail.ifBlank {
                                if (status.connection == "WAKE_LISTENING") "Microphone signal confirmed • Say Hey Jarvis"
                                else "Waiting for microphone signal • Hold mic to power off"
                            }
                            binding.tvHoldHint.visibility = View.VISIBLE
                            binding.dotStatusGlow.backgroundTintList = ColorStateList.valueOf(
                                Color.parseColor("#7A7A7A")
                            )
                        } else {
                            binding.tvHoldHint.visibility = View.VISIBLE
                            when (status.state) {
                                ConversationState.LISTENING -> {
                                    setOrbState("listening")
                                    binding.tvListeningStatus.text = "MIC ON • LISTENING..."
                                    binding.tvListeningStatus.setTextColor(Color.parseColor("#808080"))
                                    binding.tvHoldHint.text = "Speak now • Tap to mute • Hold to power off"
                                    binding.dotStatusGlow.backgroundTintList = ColorStateList.valueOf(
                                        Color.parseColor("#808080")
                                    )
                                }
                                ConversationState.SPEAKING -> {
                                    setOrbState("speaking")
                                    binding.tvListeningStatus.text = "JARVIS SPEAKING..."
                                    binding.tvListeningStatus.setTextColor(Color.parseColor("#858585"))
                                    binding.tvHoldHint.text = "Tap mic to interrupt"
                                    binding.dotStatusGlow.backgroundTintList = ColorStateList.valueOf(
                                        Color.parseColor("#858585")
                                    )
                                }
                                ConversationState.THINKING -> {
                                    setOrbState("thinking")
                                    binding.tvListeningStatus.text = "THINKING..."
                                    binding.tvListeningStatus.setTextColor(Color.parseColor("#A7A7A7"))
                                    binding.tvHoldHint.text = "Processing request..."
                                    binding.dotStatusGlow.backgroundTintList = ColorStateList.valueOf(
                                        Color.parseColor("#A7A7A7")
                                    )
                                }
                                ConversationState.IDLE -> {
                                    setOrbState("idle")
                                    binding.tvListeningStatus.text = "MIC ON • READY"
                                    binding.tvListeningStatus.setTextColor(Color.parseColor("#808080"))
                                    binding.tvHoldHint.text = "Tap to mute • Hold to power off"
                                    binding.dotStatusGlow.backgroundTintList = ColorStateList.valueOf(
                                        Color.parseColor("#808080")
                                    )
                                }
                            }
                        }
                    }
                }

                launch {
                    viewModel.audioFrequency.collect { freq ->
                        val amp = freq.amplitude
                        val bass = freq.bass
                        val high = freq.high
                        pushExternalAudio(amp, bass, high)
                    }
                }

                launch {
                    viewModel.liveError.collect { error ->
                        if (error == null) {
                            hideLiveErrorOverlay()
                        } else {
                            showLiveErrorOverlay()
                            binding.tvLiveErrorTitle.text = error.title
                            binding.tvLiveErrorMessage.text = error.message
                            binding.btnFixLiveError.text = error.actionText

                            when (error.errorCode) {
                                "API_KEY_INVALID", "API_KEY_MISSING" -> {
                                    binding.ivLiveErrorIcon.setImageResource(R.drawable.ic_warning)
                                    binding.ivLiveErrorIcon.imageTintList = ColorStateList.valueOf(Color.parseColor("#777777"))
                                    binding.tvLiveErrorTitle.setTextColor(Color.parseColor("#777777"))
                                    binding.btnFixLiveError.setOnClickListener {
                                        startActivity(Intent(requireContext(), com.jarvis.assistant.ui.settings.ApiConnectionActivity::class.java))
                                    }
                                }
                                "ACCESSIBILITY_DISABLED" -> {
                                    binding.ivLiveErrorIcon.setImageResource(R.drawable.ic_settings_shield)
                                    binding.ivLiveErrorIcon.imageTintList = ColorStateList.valueOf(Color.parseColor("#A7A7A7"))
                                    binding.tvLiveErrorTitle.setTextColor(Color.parseColor("#A7A7A7"))
                                    binding.btnFixLiveError.setOnClickListener {
                                        PermissionHelper.openAccessibilitySettings(requireContext())
                                    }
                                }
                                "PERMISSIONS_MISSING" -> {
                                    binding.ivLiveErrorIcon.setImageResource(R.drawable.ic_settings_shield)
                                    binding.ivLiveErrorIcon.imageTintList = ColorStateList.valueOf(Color.parseColor("#A7A7A7"))
                                    binding.tvLiveErrorTitle.setTextColor(Color.parseColor("#A7A7A7"))
                                    binding.btnFixLiveError.setOnClickListener {
                                        startActivity(Intent(requireContext(), com.jarvis.assistant.ui.permission.PermissionActivity::class.java))
                                    }
                                }
                                "WAKE_START_FAILED" -> {
                                    binding.btnFixLiveError.setOnClickListener {
                                        androidx.core.content.ContextCompat.startForegroundService(
                                            requireContext(), Intent(requireContext(), com.jarvis.assistant.background.JarvisConversationService::class.java)
                                                .setAction(com.jarvis.assistant.background.JarvisConversationService.ACTION_ENTER_STANDBY)
                                        )
                                    }
                                }
                                "FREE_LIMIT_REACHED" -> {
                                    binding.ivLiveErrorIcon.setImageResource(R.drawable.ic_warning)
                                    binding.ivLiveErrorIcon.imageTintList = ColorStateList.valueOf(Color.parseColor("#A7A7A7"))
                                    binding.tvLiveErrorTitle.setTextColor(Color.parseColor("#A7A7A7"))
                                    binding.btnFixLiveError.setOnClickListener {
                                        startActivity(Intent(requireContext(), com.jarvis.assistant.ui.settings.SettingsActivity::class.java))
                                    }
                                }
                                else -> {
                                    binding.ivLiveErrorIcon.setImageResource(R.drawable.ic_warning)
                                    binding.ivLiveErrorIcon.imageTintList = ColorStateList.valueOf(Color.parseColor("#777777"))
                                    binding.tvLiveErrorTitle.setTextColor(Color.parseColor("#777777"))
                                    binding.btnFixLiveError.setOnClickListener {
                                        (activity as? MainActivity)?.handleMicOrOrbClick()
                                    }
                                }
                            }
                        }
                    }
                }

                launch {
                    viewModel.connectionStatus.collect { status ->
                        if (status.startsWith("ERROR")) {
                            binding.tvListeningStatus.text = status
                            binding.dotStatusGlow.backgroundTintList = ColorStateList.valueOf(
                                Color.parseColor("#777777")
                            )
                        }
                    }
                }
            }
        }
    }

    private fun showLiveErrorOverlay() {
        if (_binding == null) return
        val card = binding.cardLiveError
        if (card.visibility == View.VISIBLE && card.alpha == 1f) return
        card.alpha = 0f
        card.scaleX = 0.85f
        card.scaleY = 0.85f
        card.visibility = View.VISIBLE
        card.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(240)
            .setInterpolator(android.view.animation.OvershootInterpolator(1.15f))
            .start()
    }

    private fun hideLiveErrorOverlay(onEnd: (() -> Unit)? = null) {
        if (_binding == null) {
            onEnd?.invoke()
            return
        }
        val card = binding.cardLiveError
        if (card.visibility != View.VISIBLE) {
            onEnd?.invoke()
            return
        }
        card.animate()
            .alpha(0f)
            .scaleX(0.85f)
            .scaleY(0.85f)
            .setDuration(180)
            .withEndAction {
                if (_binding != null) {
                    card.visibility = View.GONE
                }
                onEnd?.invoke()
            }
            .start()
    }

    private fun createTaskFallbackPanel() {
        val root = binding.root as? androidx.constraintlayout.widget.ConstraintLayout ?: return
        val panel = android.widget.LinearLayout(requireContext()).apply {
            id = View.generateViewId()
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(android.graphics.Color.rgb(12, 20, 35))
                cornerRadius = dp(16).toFloat()
                setStroke(dp(1), android.graphics.Color.rgb(34, 211, 238))
            }
            elevation = dp(8).toFloat()
            visibility = View.GONE
        }
        panel.addView(android.widget.TextView(requireContext()).apply {
            text = "JARVIS AGENT PROGRESS"
            textSize = 11f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(android.graphics.Color.rgb(103, 232, 249))
        })
        val details = android.widget.TextView(requireContext()).apply {
            textSize = 12f
            setTextColor(android.graphics.Color.WHITE)
            maxLines = 4
            setPadding(0, dp(5), 0, dp(2))
        }
        panel.addView(details)
        val params = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams(0, -2).apply {
            startToStart = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
            endToEnd = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
            topToTop = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
            topMargin = dp(70)
            marginStart = dp(16)
            marginEnd = dp(16)
        }
        root.addView(panel, params)
        taskFallbackPanel = panel
        taskFallbackText = details
    }

    private fun renderTaskFallback(snapshot: com.jarvis.assistant.automation.agent.AgentTaskSnapshot?) {
        val panel = taskFallbackPanel ?: return
        val show = snapshot != null
        panel.visibility = if (show) View.VISIBLE else View.GONE
        if (!show || snapshot == null) return
        val activeStep = snapshot.steps.firstOrNull { it.stepId == snapshot.currentStepId }?.title ?: snapshot.currentStatus
        val checklist = snapshot.steps.take(4).joinToString("\n") { step ->
            val mark = if (step.status == com.jarvis.assistant.automation.agent.TaskStepStatus.COMPLETED) "✓" else "○"
            "$mark ${step.title}"
        }
        taskFallbackText?.text = "${snapshot.originalGoal}\n${snapshot.progressPercentage}% · ${snapshot.currentStatus.replace('_', ' ')}\nCurrent: $activeStep\n$checklist"
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onDestroyView() {
        super.onDestroyView()
        orbSayHandler.removeCallbacks(orbSayFlush)
        hudSurface = null
        _binding = null
    }
}

private data class HomeStatusState(
    val isOn: Boolean,
    val isMuted: Boolean,
    val state: ConversationState,
    val isStandby: Boolean,
    val connection: String,
    val wakeDetail: String
)