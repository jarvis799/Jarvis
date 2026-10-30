package com.jarvis.assistant.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class OrbDensityTest {
    @Test fun clampsOutOfRangePercent() {
        assertEquals(OrbDensity.MIN_PARTICLES, OrbDensity.particlesFor(-10))
        assertEquals(OrbDensity.MAX_PARTICLES, OrbDensity.particlesFor(140))
    }

    @Test fun mapsPercentToParticles() {
        assertEquals(110_000, OrbDensity.particlesFor(50))
        assertEquals(OrbDensity.MIN_PARTICLES, OrbDensity.particlesFor(0))
        assertEquals(OrbDensity.MAX_PARTICLES, OrbDensity.particlesFor(100))
    }

    @Test fun roundTripsWithinOnePercent() {
        for (percent in 0..100) {
            assertTrue(abs(OrbDensity.percentFor(OrbDensity.particlesFor(percent)) - percent) <= 1)
        }
    }

    @Test fun defaultIsInsideTheRange() {
        assertTrue(OrbDensity.DEFAULT_PARTICLES in OrbDensity.MIN_PARTICLES..OrbDensity.MAX_PARTICLES)
    }
}
