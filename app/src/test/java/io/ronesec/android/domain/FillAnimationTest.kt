package io.ronesec.android.domain

import io.ronesec.android.domain.animation.FillAnimation
import io.ronesec.android.domain.model.AnimationPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FillAnimationTest {

    @Test
    fun `fillProgress function follows exact specification values`() {
        // Section 8 table:
        // t = 0.0 -> 0%
        // t = 0.125 (1s/8s) -> 25%
        // t = 0.25 (2s/8s) -> 50%
        // t = 0.375 (3s/8s) -> 75%
        // t = 0.5 (4s/8s) -> 100%
        // t = 0.625 (5s/8s) -> 75%
        // t = 0.75 (6s/8s) -> 50%
        // t = 0.875 (7s/8s) -> 25%
        // t = 1.0 (8s/8s) -> 0%
        assertEquals(0.0f, FillAnimation.fillProgress(0.0f), 0.001f)
        assertEquals(0.25f, FillAnimation.fillProgress(0.125f), 0.001f)
        assertEquals(0.50f, FillAnimation.fillProgress(0.25f), 0.001f)
        assertEquals(0.75f, FillAnimation.fillProgress(0.375f), 0.001f)
        assertEquals(1.0f, FillAnimation.fillProgress(0.50f), 0.001f)
        assertEquals(0.75f, FillAnimation.fillProgress(0.625f), 0.001f)
        assertEquals(0.50f, FillAnimation.fillProgress(0.75f), 0.001f)
        assertEquals(0.25f, FillAnimation.fillProgress(0.875f), 0.001f)
        assertEquals(0.0f, FillAnimation.fillProgress(1.0f), 0.001f)
    }

    @Test
    fun `animation reports correct phases and progress during 8 second cycle`() {
        val animation = FillAnimation()
        val start = 1_000_000L
        animation.start(durationMs = 8_000L, startTimeMs = start)

        // t = 0s
        assertEquals(AnimationPhase.INHALE, animation.phase(start))
        assertEquals(0.0f, animation.progress(start), 0.001f)
        assertFalse(animation.isFinished(start))

        // t = 2s (25% duration)
        assertEquals(AnimationPhase.INHALE, animation.phase(start + 2_000L))
        assertEquals(0.5f, animation.progress(start + 2_000L), 0.001f)

        // t = 4s (50% duration - peak inhale)
        assertEquals(AnimationPhase.EXHALE, animation.phase(start + 4_000L))
        assertEquals(1.0f, animation.progress(start + 4_000L), 0.001f)

        // t = 6s (75% duration)
        assertEquals(AnimationPhase.EXHALE, animation.phase(start + 6_000L))
        assertEquals(0.5f, animation.progress(start + 6_000L), 0.001f)

        // t = 8s (100% duration - complete)
        assertEquals(AnimationPhase.COMPLETE, animation.phase(start + 8_000L))
        assertEquals(0.0f, animation.progress(start + 8_000L), 0.001f)
        assertTrue(animation.isFinished(start + 8_000L))
    }

    @Test
    fun `eased progress stays strictly within 0 and 1 without overshoot`() {
        val animation = FillAnimation()
        val start = 1_000_000L
        animation.start(durationMs = 8_000L, startTimeMs = start)

        for (offset in 0L..8_000L step 200L) {
            val eased = animation.easedProgress(start + offset)
            assertTrue("Eased progress should be >= 0.0, was $eased", eased >= 0.0f)
            assertTrue("Eased progress should be <= 1.0, was $eased", eased <= 1.0f)
        }
    }
}
