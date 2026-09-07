package io.ronesec.android.domain.util

import org.junit.Assert.assertEquals
import org.junit.Test

class TimeFormatUtilsTest {

    @Test
    fun `formatDurationRu formats minutes and seconds correctly`() {
        assertEquals("0 сек", TimeFormatUtils.formatDurationRu(0L))
        assertEquals("30 сек", TimeFormatUtils.formatDurationRu(30_000L))
        assertEquals("1 мин", TimeFormatUtils.formatDurationRu(60_000L))
        assertEquals("1 мин 30 с", TimeFormatUtils.formatDurationRu(90_000L))
        assertEquals("5 мин", TimeFormatUtils.formatDurationRu(300_000L))
        assertEquals("2 мин 15 с", TimeFormatUtils.formatDurationRu(135_000L))
    }

    @Test
    fun `parseDuration handles valid inputs, empty strings and defaults`() {
        assertEquals(90_000L, TimeFormatUtils.parseDuration("1", "30"))
        assertEquals(120_000L, TimeFormatUtils.parseDuration("2", "0"))
        assertEquals(30_000L, TimeFormatUtils.parseDuration("", "30"))
        assertEquals(60_000L, TimeFormatUtils.parseDuration("1", ""))
        // When both empty or zero, falls back to minMs (default 5000L)
        assertEquals(5_000L, TimeFormatUtils.parseDuration("", ""))
        assertEquals(5_000L, TimeFormatUtils.parseDuration("0", "0"))
    }

    @Test
    fun `parseDuration respects limits`() {
        assertEquals(10_000L, TimeFormatUtils.parseDuration("0", "2", minMs = 10_000L))
        assertEquals(3600_000L, TimeFormatUtils.parseDuration("100", "0", maxMs = 3600_000L))
    }

    @Test
    fun `calculateAdjustedTime adjusts minutes and seconds correctly`() {
        // +1 minute from 2m 0s -> 3m 0s
        val plus1m = TimeFormatUtils.calculateAdjustedTime("2", "0", 60_000L)
        assertEquals("3", plus1m.first)
        assertEquals("0", plus1m.second)

        // -1 minute from 2m 0s -> 1m 0s
        val minus1m = TimeFormatUtils.calculateAdjustedTime("2", "0", -60_000L)
        assertEquals("1", minus1m.first)
        assertEquals("0", minus1m.second)

        // +15s from 1m 50s -> 2m 5s
        val plus15s = TimeFormatUtils.calculateAdjustedTime("1", "50", 15_000L)
        assertEquals("2", plus15s.first)
        assertEquals("5", plus15s.second)

        // -30s from 0m 45s -> 0m 15s
        val minus30s = TimeFormatUtils.calculateAdjustedTime("0", "45", -30_000L)
        assertEquals("0", minus30s.first)
        assertEquals("15", minus30s.second)

        // Underflow is clamped to minMs (5s -> 0m 5s)
        val underflow = TimeFormatUtils.calculateAdjustedTime("0", "10", -30_000L, minMs = 5_000L)
        assertEquals("0", underflow.first)
        assertEquals("5", underflow.second)
    }
}
