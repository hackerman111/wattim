package io.ronesec.android.ui.designsystem

import io.ronesec.domain.model.AnimationMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BreathingVisualsTest {

    @Test
    fun fillHeightFractionHonorsBoundsAndCompletion() {
        // Between 0.15 and 0.85 during normal animation
        for (ms in 0L..10_000L step 400L) {
            val fraction = BreathingGeometry.fillHeightFraction(ms, 0.5f, false)
            assertTrue("Fraction $fraction must be >= 0.15", fraction >= 0.15f)
            assertTrue("Fraction $fraction must be <= 0.85", fraction <= 0.85f)
        }

        // Completion reaches 1.0f
        assertEquals(1.0f, BreathingGeometry.fillHeightFraction(1500L, 1.0f, false), 0.001f)
        assertEquals(1.0f, BreathingGeometry.fillHeightFraction(1500L, 1.2f, false), 0.001f)

        // Reduced motion returns 0.5f
        assertEquals(0.5f, BreathingGeometry.fillHeightFraction(1500L, 0.3f, true), 0.001f)
    }

    @Test
    fun pulseSineHonorsBoundsAndCompletion() {
        for (ms in 0L..10_000L step 250L) {
            val sine = BreathingGeometry.pulseSine(ms, 0.5f, false)
            assertTrue("Pulse $sine must be >= 0.0", sine >= 0.0f)
            assertTrue("Pulse $sine must be <= 1.0", sine <= 1.0f)
        }

        // Completion reaches 1.0f
        assertEquals(1.0f, BreathingGeometry.pulseSine(1000L, 1.0f, false), 0.001f)

        // Reduced motion returns 0.5f
        assertEquals(0.5f, BreathingGeometry.pulseSine(1000L, 0.3f, true), 0.001f)
    }

    @Test
    fun rippleFractionIsPeriodicAndBounded() {
        for (ring in 0 until BreathingGeometry.RIPPLE_RING_COUNT) {
            for (ms in 0L..8_000L step 500L) {
                val fraction = BreathingGeometry.rippleFraction(ms, ring, false)
                assertTrue("Ripple $fraction must be >= 0.0", fraction >= 0.0f)
                assertTrue("Ripple $fraction <= 1.0", fraction <= 1.0f)
            }
            // Reduced motion produces constant spaced fractions
            val reduced = BreathingGeometry.rippleFraction(1234L, ring, true)
            assertTrue(reduced in 0.1f..0.9f)
        }
    }

    @Test
    fun allFiveAnimationModesAreUntimed() {
        for (mode in AnimationMode.entries) {
            org.junit.Assert.assertFalse(mode.revealsRemainingTime)
        }
    }
}
