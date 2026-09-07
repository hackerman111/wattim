package io.ronesec.android.domain.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalTime

class ScheduleTimeTest {

    @Test
    fun `test minute wrapping with mod`() {
        assertEquals(55, (0 - 5).mod(60))
        assertEquals(59, (0 - 1).mod(60))
        assertEquals(0, (59 + 1).mod(60))
        assertEquals(4, (59 + 5).mod(60))
        assertEquals(30, (25 + 5).mod(60))
    }

    @Test
    fun `test hour wrapping with mod`() {
        assertEquals(23, (0 - 1).mod(24))
        assertEquals(0, (23 + 1).mod(24))
        assertEquals(9, (8 + 1).mod(24))
    }

    @Test
    fun `test LocalTime creation with custom hours and minutes`() {
        val time = LocalTime.of(9, 30)
        assertEquals(9, time.hour)
        assertEquals(30, time.minute)
    }
}
