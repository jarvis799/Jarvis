package com.jarvis.assistant.util

/** Maps the orb density slider (0-100) to the particle count the orb renders. */
object OrbDensity {
    const val MIN_PARTICLES = 20_000
    const val MAX_PARTICLES = 200_000
    const val DEFAULT_PARTICLES = 80_000

    fun particlesFor(percent: Int): Int {
        val p = percent.coerceIn(0, 100)
        return MIN_PARTICLES + (MAX_PARTICLES - MIN_PARTICLES) * p / 100
    }

    fun percentFor(particles: Int): Int {
        val clamped = particles.coerceIn(MIN_PARTICLES, MAX_PARTICLES)
        return Math.round((clamped - MIN_PARTICLES) * 100f / (MAX_PARTICLES - MIN_PARTICLES))
    }
}
