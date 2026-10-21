package com.jarvis.assistant.ui.voice

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.jarvis.assistant.R

/**
 * DEV STYLE: premium glassmorphism voice overlay. A rounded, near-black glass surface
 * with a thin luminous border and ambient cyan, blue, violet and magenta light, plus a
 * separate bottom glass panel for typing and status. No orb, avatar or decoration.
 */
class VoiceOverlayPanel(
    context: Context,
    private val keyboardEnabled: Boolean,
    private val onSend: (String) -> Unit,
    private val onMicToggle: () -> Unit,
    private val onClose: () -> Unit
) : FrameLayout(context) {

    private val content = LinearLayout(context)
    private val accentStrip = View(context)
    private val dotHalo = View(context)
    private val statusDot = View(context)
    private val statusText = TextView(context)
    private val transcriptText = TextView(context)
    private val responseText = TextView(context)
    private val responseScroll = ScrollView(context)
    private val inputRow = LinearLayout(context)
    private val inputField = EditText(context)
    private val micButton = ImageView(context)
    private val keyboardButton = ImageView(context)
    private val closeButton = ImageView(context)
    private val sendButton = ImageView(context)

    private val density = resources.displayMetrics.density
    private var stripPulse: android.animation.ObjectAnimator? = null

    private fun dp(value: Int): Int = (value * density).toInt()
    private fun dpf(value: Float): Float = value * density

    init {
        setBackgroundColor(VoiceOverlayGlass.SCRIM)
        addView(glow(VoiceOverlayGlass.MAGENTA, 150, 360, Gravity.TOP or Gravity.END, -70, -90))
        addView(glow(VoiceOverlayGlass.BLUE, 145, 360, Gravity.BOTTOM or Gravity.START, -80, -70))
        addView(glow(VoiceOverlayGlass.VIOLET, 90, 320, Gravity.TOP or Gravity.CENTER_HORIZONTAL, 0, 150))

        content.orientation = LinearLayout.VERTICAL
        content.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        content.setPadding(dp(18), dp(28), dp(18), dp(18))
        content.addView(mainCard(), LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        content.addView(bottomPanel(), LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = dp(12) })
        addView(content)

        renderState(VoiceOverlayState.WAKE)
        renderMic(false)
        startAmbientPulse()
    }

    /** Slow breathing glow on the accent strip so the surface feels alive. */
    private fun startAmbientPulse() {
        stripPulse = android.animation.ObjectAnimator.ofFloat(accentStrip, "alpha", 0.45f, 1f).apply {
            duration = 2400L
            repeatCount = android.animation.ValueAnimator.INFINITE
            repeatMode = android.animation.ValueAnimator.REVERSE
            interpolator = android.view.animation.AccelerateDecelerateInterpolator()
            start()
        }
    }

    override fun onDetachedFromWindow() {
        stripPulse?.cancel()
        stripPulse = null
        super.onDetachedFromWindow()
    }

    private fun glow(color: Int, alpha: Int, radiusDp: Int, gravity: Int, dx: Int, dy: Int): View =
        View(context).apply {
            background = VoiceOverlayGlass.radialGlow(color, alpha, dpf(radiusDp.toFloat()))
            layoutParams = LayoutParams(dp(radiusDp), dp(radiusDp), gravity).apply {
                leftMargin = dp(dx)
                topMargin = dp(dy)
            }
        }

    private fun mainCard(): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = VoiceOverlayGlass.glassCard(dpf(30f))
            setPadding(dp(22), dp(22), dp(22), dp(22))
            addView(accentStrip.apply {
                background = VoiceOverlayGlass.accentStrip(dpf(2f))
                layoutParams = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(3)).apply { bottomMargin = dp(14) }
            })
            addView(statusRow())
            addView(transcriptText.apply {
                textSize = 13f
                setTextColor(VoiceOverlayGlass.TEXT_SECONDARY)
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
                visibility = View.GONE
                setPadding(0, dp(10), 0, 0)
            })
            addView(responseScroll.apply {
                layoutParams = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f)
                isVerticalScrollBarEnabled = false
                addView(responseText.apply {
                    layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
                    textSize = 15f
                    setTextColor(VoiceOverlayGlass.TEXT_PRIMARY)
                    setLineSpacing(dpf(4f), 1f)
                    setPadding(0, dp(14), 0, 0)
                })
            })
        }

    private fun statusRow(): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(FrameLayout(context).apply {
                layoutParams = LinearLayout.LayoutParams(dp(22), dp(22))
                addView(dotHalo.apply {
                    background = VoiceOverlayGlass.radialGlow(VoiceOverlayGlass.CYAN, 150, dpf(11f))
                    layoutParams = FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
                })
                addView(statusDot.apply {
                    background = VoiceOverlayGlass.dot(VoiceOverlayGlass.CYAN)
                    layoutParams = FrameLayout.LayoutParams(dp(9), dp(9), Gravity.CENTER)
                })
            })
            addView(statusText.apply {
                textSize = 11f
                letterSpacing = 0.18f
                setTextColor(VoiceOverlayGlass.TEXT_PRIMARY)
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                setPadding(dp(10), 0, 0, 0)
            })
        }

    private fun bottomPanel(): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = VoiceOverlayGlass.glassPanel(dpf(26f))
            setPadding(dp(14), dp(14), dp(14), dp(14))
            inputRow.apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                visibility = if (keyboardEnabled) View.VISIBLE else View.GONE
                addView(inputField.apply {
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    hint = "Type a message for Jarvis"
                    setHintTextColor(VoiceOverlayGlass.TEXT_MUTED)
                    setTextColor(VoiceOverlayGlass.TEXT_PRIMARY)
                    textSize = 14f
                    maxLines = 3
                    background = VoiceOverlayGlass.fieldShell(dpf(18f))
                    setPadding(dp(14), dp(12), dp(14), dp(12))
                    inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                    imeOptions = EditorInfo.IME_ACTION_SEND
                    setOnEditorActionListener { _, actionId, _ ->
                        if (actionId == EditorInfo.IME_ACTION_SEND) {
                            submit()
                            true
                        } else {
                            false
                        }
                    }
                    addTextChangedListener(object : android.text.TextWatcher {
                        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                            val empty = s.isNullOrBlank()
                            sendButton.alpha = if (empty) 0.55f else 1f
                            sendButton.imageTintList = ColorStateList.valueOf(
                                if (empty) VoiceOverlayGlass.TEXT_MUTED else VoiceOverlayGlass.CYAN
                            )
                        }
                        override fun afterTextChanged(s: android.text.Editable?) {}
                    })
                })
                addView(sendButton.apply {
                    layoutParams = LinearLayout.LayoutParams(dp(44), dp(44)).apply { leftMargin = dp(8) }
                    setImageResource(R.drawable.ic_send_up)
                    imageTintList = ColorStateList.valueOf(VoiceOverlayGlass.TEXT_MUTED)
                    background = VoiceOverlayGlass.pill(VoiceOverlayGlass.SEAT_FILL, 0x66FFFFFF, dpf(22f))
                    setPadding(dp(10), dp(10), dp(10), dp(10))
                    alpha = 0.55f
                    contentDescription = "Send message"
                    setOnClickListener { submit() }
                })
            }
            addView(inputRow)
            addView(buttonRow(), LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = if (keyboardEnabled) dp(10) else 0
            })
        }

    private fun buttonRow(): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(micButton.apply {
                layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
                setImageResource(R.drawable.ic_mic_jarvis)
                imageTintList = ColorStateList.valueOf(VoiceOverlayGlass.CYAN)
                background = VoiceOverlayGlass.pill(VoiceOverlayGlass.SEAT_FILL, 0x4DFFFFFF, dpf(24f))
                setPadding(dp(12), dp(12), dp(12), dp(12))
                contentDescription = "Microphone"
                setOnClickListener { onMicToggle() }
            })
            if (keyboardEnabled) {
                addView(keyboardButton.apply {
                    layoutParams = LinearLayout.LayoutParams(dp(48), dp(48)).apply { leftMargin = dp(10) }
                    setImageResource(R.drawable.ic_edit)
                    imageTintList = ColorStateList.valueOf(VoiceOverlayGlass.TEXT_PRIMARY)
                    background = VoiceOverlayGlass.pill(VoiceOverlayGlass.SEAT_FILL, 0x4DFFFFFF, dpf(24f))
                    setPadding(dp(12), dp(12), dp(12), dp(12))
                    contentDescription = "Keyboard"
                    setOnClickListener { focusInput() }
                })
            }
            addView(View(context).apply { layoutParams = LinearLayout.LayoutParams(0, 1, 1f) })
            addView(closeButton.apply {
                layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))
                setImageResource(R.drawable.ic_close)
                imageTintList = ColorStateList.valueOf(VoiceOverlayGlass.TEXT_SECONDARY)
                background = VoiceOverlayGlass.pill(VoiceOverlayGlass.SEAT_FILL, 0x33FFFFFF, dpf(22f))
                setPadding(dp(10), dp(10), dp(10), dp(10))
                contentDescription = "Close Jarvis overlay"
                setOnClickListener { onClose() }
            })
        }

    fun renderState(state: VoiceOverlayState) {
        statusText.text = state.label
        statusDot.background = VoiceOverlayGlass.dot(colorFor(state))
        dotHalo.background = VoiceOverlayGlass.radialGlow(colorFor(state), 150, dpf(11f))
        statusDot.animate().cancel()
        statusDot.alpha = 1f
        if (state == VoiceOverlayState.LISTENING || state == VoiceOverlayState.SPEAKING) {
            statusDot.animate().alpha(0.35f).setDuration(650L).withEndAction {
                statusDot.animate().alpha(1f).setDuration(650L).start()
            }.start()
        }
    }

    fun flashWake() {
        renderState(VoiceOverlayState.WAKE)
    }

    fun setTranscript(text: String) {
        val clean = text.trim()
        transcriptText.text = clean
        transcriptText.visibility = if (clean.isEmpty()) View.GONE else View.VISIBLE
    }

    fun setResponse(text: String) {
        responseText.text = text
        if (text.isNotEmpty()) {
            responseScroll.post { responseScroll.fullScroll(View.FOCUS_DOWN) }
        }
    }

    fun appendTyped(text: String) {
        setTranscript(text)
        setResponse("")
    }

    fun renderMic(muted: Boolean) {
        micButton.setImageResource(if (muted) R.drawable.ic_mic_mute_jarvis else R.drawable.ic_mic_jarvis)
        micButton.imageTintList = ColorStateList.valueOf(if (muted) VoiceOverlayGlass.TEXT_MUTED else VoiceOverlayGlass.CYAN)
    }

    fun applyInsets(imeBottomPx: Int, statusBarTopPx: Int) {
        content.setPadding(dp(18), dp(28) + statusBarTopPx, dp(18), dp(18) + imeBottomPx)
    }

    fun focusInput() {
        if (!keyboardEnabled) return
        inputField.requestFocus()
        val manager = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        manager?.showSoftInput(inputField, InputMethodManager.SHOW_IMPLICIT)
    }

    fun dismissKeyboard() {
        val manager = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        manager?.hideSoftInputFromWindow(inputField.windowToken, 0)
        inputField.clearFocus()
    }

    private fun submit() {
        val text = inputField.text.toString().trim()
        if (text.isEmpty()) return
        inputField.setText("")
        dismissKeyboard()
        onSend(text)
    }

    private fun colorFor(state: VoiceOverlayState): Int = when (state) {
        VoiceOverlayState.WAKE -> VoiceOverlayGlass.CYAN
        VoiceOverlayState.LISTENING -> VoiceOverlayGlass.CYAN
        VoiceOverlayState.PROCESSING -> VoiceOverlayGlass.VIOLET
        VoiceOverlayState.SPEAKING -> VoiceOverlayGlass.MAGENTA
        VoiceOverlayState.IDLE -> VoiceOverlayGlass.TEXT_MUTED
    }
}
