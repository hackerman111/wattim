package io.ronesec.domain.breathing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BreathingTimelineTest {

    @Test
    fun testBreathingPhasesAndSmoothstep() {
        val duration = 8_000L
        val start = 100_000L

        // At start (e = 0)
        val p0 = BreathingTimeline.calculate(start, start, duration)
        assertEquals(BreathingPhase.INHALE, p0.phase)
        assertEquals(0.0, p0.p, 0.001)
        assertEquals(0.0, p0.t, 0.001)
        assertFalse(p0.isComplete)
        assertEquals("08.0", p0.formattedCountdown)

        // At quarter time (e = 2000, t = 0.25)
        val pQuarter = BreathingTimeline.calculate(start, start + 2_000L, duration)
        assertEquals(BreathingPhase.INHALE, pQuarter.phase)
        assertEquals(0.25, pQuarter.t, 0.001)
        // q = 2 * 0.25 = 0.5, p = 3*(0.25) - 2*(0.125) = 0.75 - 0.25 = 0.5
        assertEquals(0.5, pQuarter.p, 0.001)
        assertFalse(pQuarter.isComplete)
        assertEquals("06.0", pQuarter.formattedCountdown)

        // At midpoint (e = 4000, t = 0.5)
        val pHalf = BreathingTimeline.calculate(start, start + 4_000L, duration)
        assertEquals(BreathingPhase.EXHALE, pHalf.phase)
        assertEquals(0.5, pHalf.t, 0.001)
        // q = 2 * 0.5 = 1.0, p = 3 - 2 = 1.0
        assertEquals(1.0, pHalf.p, 0.001)
        assertFalse(pHalf.isComplete)
        assertEquals("04.0", pHalf.formattedCountdown)

        // At 3/4 time (e = 6000, t = 0.75)
        val p3Quarter = BreathingTimeline.calculate(start, start + 6_000L, duration)
        assertEquals(BreathingPhase.EXHALE, p3Quarter.phase)
        assertEquals(0.75, p3Quarter.t, 0.001)
        // q = 2 * (1 - 0.75) = 0.5, p = 0.5
        assertEquals(0.5, p3Quarter.p, 0.001)
        assertFalse(p3Quarter.isComplete)
        assertEquals("02.0", p3Quarter.formattedCountdown)

        // Exactly at end (e = 8000, t = 1.0)
        val pEnd = BreathingTimeline.calculate(start, start + 8_000L, duration)
        assertEquals(BreathingPhase.COMPLETE, pEnd.phase)
        assertEquals(1.0, pEnd.t, 0.001)
        assertEquals(0.0, pEnd.p, 0.001) // at completion hold zero amplitude
        assertTrue(pEnd.isComplete)
        assertEquals("00.0", pEnd.formattedCountdown)

        // Beyond end (e = 9000)
        val pBeyond = BreathingTimeline.calculate(start, start + 9_000L, duration)
        assertEquals(BreathingPhase.COMPLETE, pBeyond.phase)
        assertTrue(pBeyond.isComplete)
        assertEquals(0.0, pBeyond.p, 0.001)
        assertEquals("00.0", pBeyond.formattedCountdown)
    }

    @Test
    fun testCountdownRoundingToTenths() {
        val duration = 10_000L
        val start = 0L

        // remaining = 4321 ms -> 44 tenths -> 4.4 seconds -> "04.4"
        val p1 = BreathingTimeline.calculate(start, start + 5_679L, duration)
        assertEquals("04.4", p1.formattedCountdown)

        // remaining = 1 ms -> 1 tenth -> 0.1 seconds -> "00.1"
        val p2 = BreathingTimeline.calculate(start, start + 9_999L, duration)
        assertEquals("00.1", p2.formattedCountdown)
    }
}
