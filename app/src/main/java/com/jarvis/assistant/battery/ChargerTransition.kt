package com.jarvis.assistant.battery

/** Silent baseline, then exactly one announcement per physical transition. */
class ChargerTransition {
    private var powered: Boolean? = null
    private var fullSpoken = false
    fun accept(connected: Boolean, full: Boolean, charging: Boolean): String? {
        val previous = powered
        powered = connected
        if (previous == null) { fullSpoken = connected && full; return null }
        if (!connected) {
            fullSpoken = false
            return if (previous) "unplugged" else null
        }
        if (full && !fullSpoken) { fullSpoken = true; return "full" }
        if (!previous) return if (charging) "charging" else "connected"
        return null
    }
}