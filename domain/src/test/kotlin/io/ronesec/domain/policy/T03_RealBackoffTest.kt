package io.ronesec.domain.policy

import io.ronesec.domain.model.BackoffConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class T03_RealBackoffTest {

    @Test
    fun formulaExactValuesAndCap() {
        val baseDurationMs = 8_000L
        val growthPercent = 20

        // N = 0 -> base
        assertEquals(8_000L, Backoff.calculateDelayMs(baseDurationMs, growthPercent, 0))

        // N = 1 -> 8000 * 1.20 = 9600
        assertEquals(9_600L, Backoff.calculateDelayMs(baseDurationMs, growthPercent, 1))

        // N = 2 -> 8000 * 1.44 = 11520
        assertEquals(11_520L, Backoff.calculateDelayMs(baseDurationMs, growthPercent, 2))

        // N = 3 -> 8000 * 1.728 = 13824
        assertEquals(13_824L, Backoff.calculateDelayMs(baseDurationMs, growthPercent, 3))

        // Very large N -> capped at 300,000 ms (300 seconds)
        assertEquals(300_000L, Backoff.calculateDelayMs(baseDurationMs, growthPercent, 100))
    }

    @Test
    fun firstTenCalculatorProducesTenValues() {
        val baseDurationMs = 8_000L
        val growthPercent = 20

        val firstTen = Backoff.calculateFirstTen(baseDurationMs, growthPercent)
        assertEquals(10, firstTen.size)
        assertEquals(8_000L, firstTen[0])
        assertEquals(9_600L, firstTen[1])
        assertTrue(firstTen.all { it in 1_000L..300_000L })
    }

    @Test
    fun rollingWindowCountsEligiblePriorEntries() {
        val now = 1_000_000_000L
        val window60m = 60 * 60 * 1000L // 3,600,000 ms

        val entries = listOf(
            now - 4_000_000L, // outside (> 60m ago)
            now - 3_500_000L, // inside
            now - 1_000_000L, // inside
            now - 100_000L,   // inside
            now               // inside (boundary)
        )

        val count = Backoff.countEligiblePriorEntries(entries, now, window60m)
        assertEquals(4, count)
    }

    @Test
    fun disabledGrowthUsesBase() {
        val config = BackoffConfig(enabled = false, percent = 20, windowMs = 3600_000L)
        val delay = Backoff.resolveEffectiveDurationMs(8_000L, config, priorEntriesCount = 5)
        assertEquals(8_000L, delay)
    }
}
