package io.ronesec.domain.breathing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AttentionCheckScheduleTest {

    @Test
    fun generate_zeroCount_returnsEmpty() {
        val schedule = AttentionCheckSchedule.generate(10_000L, 0)
        assertTrue(schedule.isEmpty())
    }

    @Test
    fun generate_singleCheck_withinBounds() {
        val schedule = AttentionCheckSchedule.generate(
            durationMs = 10_000L,
            count = 1,
            randomProvider = { min: Long, max: Long -> (min + max) / 2 }
        )
        assertEquals(1, schedule.size)
        assertEquals(5_000L, schedule[0])
    }

    @Test
    fun generate_multipleChecks_strictlyAscendingAndWithinBounds() {
        val durationMs = 20_000L
        val schedule = AttentionCheckSchedule.generate(
            durationMs = durationMs,
            count = 3,
            randomProvider = { min: Long, max: Long -> (min + max) / 2 }
        )
        assertEquals(3, schedule.size)
        assertTrue(schedule[0] >= (durationMs * 0.15).toLong())
        assertTrue(schedule[2] <= (durationMs * 0.85).toLong())
        assertTrue(schedule[0] < schedule[1])
        assertTrue(schedule[1] < schedule[2])
    }
}
