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

    private var active: Boolean? = null

    fun apply(enabled: Boolean) {
        if (active == enabled) return
        active = enabled
        val visibility = (0 until binding.root.childCount).associate {
            binding.root.getChildAt(it).let { child -> child.id to child.visibility }
        }
        original.applyTo(binding.root)
        for (index in 0 until binding.root.childCount) {
            val child = binding.root.getChildAt(index)
            visibility[child.id]?.let { child.visibility = it }
        }
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
            connect(R.id.orbContainer, ConstraintSet.BOTTOM, R.id.layoutFeatureEntries, ConstraintSet.TOP, 8)
            constrainHeight(R.id.orbContainer, 0)
            constrainDefaultHeight(R.id.orbContainer, ConstraintSet.MATCH_CONSTRAINT_SPREAD)
            applyTo(binding.root)
        }
    }
}
