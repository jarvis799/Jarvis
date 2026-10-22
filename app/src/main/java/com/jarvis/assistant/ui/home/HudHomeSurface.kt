package com.jarvis.assistant.ui.home

import android.view.View
import androidx.constraintlayout.widget.ConstraintSet
import com.jarvis.assistant.R
import com.jarvis.assistant.databinding.FragmentHomeBinding

class HudHomeSurface(private val binding: FragmentHomeBinding) {
    private val original = ConstraintSet().apply { clone(binding.root) }
    private val decor = listOf(binding.glowOrbTopRight, binding.glowOrbCenterLeft, binding.glowOrbBottom, binding.orbHaloRing, binding.layoutStatusIndicator)
    private val cards = listOf(binding.cardWeather, binding.btnScreenShareQuick, binding.btnSavedMemories, binding.btnConversationLanguageQuick)
    private val backgrounds = cards.map { it.background }

    fun apply(enabled: Boolean) {
        original.applyTo(binding.root)
        decor.forEach { it.visibility = if (enabled) View.GONE else View.VISIBLE }
        cards.forEachIndexed { index, view ->
            if (enabled) view.setBackgroundResource(R.drawable.bg_hud_panel)
            else view.background = backgrounds[index]
        }
        if (!enabled) return
        ConstraintSet().apply {
            clone(binding.root)
            clear(R.id.orbContainer, ConstraintSet.TOP)
            clear(R.id.orbContainer, ConstraintSet.BOTTOM)
            connect(R.id.orbContainer, ConstraintSet.TOP, R.id.layoutTitle, ConstraintSet.BOTTOM, 4)
            connect(R.id.orbContainer, ConstraintSet.BOTTOM, R.id.btnGmailSetup, ConstraintSet.TOP, 8)
            constrainHeight(R.id.orbContainer, 0)
            constrainDefaultHeight(R.id.orbContainer, ConstraintSet.MATCH_CONSTRAINT_SPREAD)
            applyTo(binding.root)
        }
    }
}
