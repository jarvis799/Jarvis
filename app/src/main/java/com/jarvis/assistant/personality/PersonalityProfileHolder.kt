package com.jarvis.assistant.personality

import android.content.Context

/**
 * Process-wide live copy of the personality profile so prompt building (which has no
 * Context) always sees the boss's latest saved settings without extra plumbing.
 */
object PersonalityProfileHolder {

    @Volatile
    var current: PersonalityProfile? = null
        private set

    fun refresh(context: Context) {
        current = PersonalityStore(context).load()
    }

    fun update(profile: PersonalityProfile) {
        current = profile
    }
}