package io.ronesec.domain.policy

import io.ronesec.domain.model.CompiledSchedule
import io.ronesec.domain.model.ScheduleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

class T02_ScheduleResolverTest {

    private val zoneId = ZoneId.of("UTC")
    private val targetPackage = "com.example.target"

    @Test
    fun mondayOvernight_startDaySemantics() {
        // Monday 23:00 to 07:00 (overnight)
        // Weekday mask: Monday is bit 0 (1 shl 0 = 1)
        val mondayMask = 1 shl 0
        val schedule = CompiledSchedule(
            id = 1L,
            name = "Mon Night",
            weekdayMask = mondayMask,
            startMinute = 23 * 60, // 23:00
            endMinute = 7 * 60,    // 07:00
            enabled = true,
            type = ScheduleType.HARD_BLOCK,
            targetPackages = setOf(targetPackage)
        )

        // 2026-09-07 is Monday
        // Mon 06:30 (before Monday 23:00) -> NOT active
        val monMorning = ZonedDateTime.of(2026, 9, 7, 6, 30, 0, 0, zoneId).toInstant()
        val resMonMorning = ScheduleResolver.resolveActiveHardBlock(listOf(schedule), targetPackage, monMorning, zoneId)
        assertNull("Monday 06:30 should not be covered by Monday night schedule", resMonMorning)

        // Mon 23:30 -> ACTIVE
        val monNight = ZonedDateTime.of(2026, 9, 7, 23, 30, 0, 0, zoneId).toInstant()
        val resMonNight = ScheduleResolver.resolveActiveHardBlock(listOf(schedule), targetPackage, monNight, zoneId)
        assertNotNull("Monday 23:30 should be active", resMonNight)
        assertTrue(resMonNight!!.isBlocked)
        val expectedTuesdayEnd = ZonedDateTime.of(2026, 9, 8, 7, 0, 0, 0, zoneId).toInstant()
        assertEquals(expectedTuesdayEnd, resMonNight.until)

        // Tue 06:30 -> ACTIVE (belongs to Monday start day overnight)
        val tueMorning = ZonedDateTime.of(2026, 9, 8, 6, 30, 0, 0, zoneId).toInstant()
        val resTueMorning = ScheduleResolver.resolveActiveHardBlock(listOf(schedule), targetPackage, tueMorning, zoneId)
        assertNotNull("Tuesday 06:30 should be active", resTueMorning)
        assertEquals(expectedTuesdayEnd, resTueMorning!!.until)

        // Tue 07:00 -> NOT active (half-open [start, end))
        val tueExactEnd = ZonedDateTime.of(2026, 9, 8, 7, 0, 0, 0, zoneId).toInstant()
        val resTueEnd = ScheduleResolver.resolveActiveHardBlock(listOf(schedule), targetPackage, tueExactEnd, zoneId)
        assertNull("Tuesday 07:00 is exact end, half-open should not cover it", resTueEnd)
    }

    @Test
    fun sundayOvernight_intoMonday() {
        // Sunday 23:00 to 07:00 (overnight)
        // Sunday is bit 6 (1 shl 6 = 64)
        val sundayMask = 1 shl 6
        val schedule = CompiledSchedule(
            id = 2L,
            name = "Sun Night",
            weekdayMask = sundayMask,
            startMinute = 23 * 60,
            endMinute = 7 * 60,
            enabled = true,
            type = ScheduleType.HARD_BLOCK,
            targetPackages = setOf(targetPackage)
        )

        // 2026-09-06 is Sunday, 2026-09-07 is Monday
        val sunNight = ZonedDateTime.of(2026, 9, 6, 23, 30, 0, 0, zoneId).toInstant()
        val resSun = ScheduleResolver.resolveActiveHardBlock(listOf(schedule), targetPackage, sunNight, zoneId)
        assertNotNull(resSun)

        val monEarlyMorning = ZonedDateTime.of(2026, 9, 7, 6, 45, 0, 0, zoneId).toInstant()
        val resMon = ScheduleResolver.resolveActiveHardBlock(listOf(schedule), targetPackage, monEarlyMorning, zoneId)
        assertNotNull("Monday early morning should be covered by Sunday night", resMon)
    }

    @Test
    fun contiguousUnionOfTouchingHardBlocks() {
        // 10:00 - 12:00 and 12:00 - 14:00 on Monday
        val mondayMask = 1 shl 0
        val sched1 = CompiledSchedule(
            id = 10L,
            name = "Block 1",
            weekdayMask = mondayMask,
            startMinute = 10 * 60,
            endMinute = 12 * 60,
            enabled = true,
            type = ScheduleType.HARD_BLOCK,
            targetPackages = setOf(targetPackage)
        )
        val sched2 = CompiledSchedule(
            id = 11L,
            name = "Block 2",
            weekdayMask = mondayMask,
            startMinute = 12 * 60,
            endMinute = 14 * 60,
            enabled = true,
            type = ScheduleType.HARD_BLOCK,
            targetPackages = setOf(targetPackage)
        )

        val at11 = ZonedDateTime.of(2026, 9, 7, 11, 0, 0, 0, zoneId).toInstant()
        val res = ScheduleResolver.resolveActiveHardBlock(listOf(sched1, sched2), targetPackage, at11, zoneId)
        assertNotNull(res)
        val expectedUnionEnd = ZonedDateTime.of(2026, 9, 7, 14, 0, 0, 0, zoneId).toInstant()
        assertEquals("Contiguous union must extend through the end of the second touching block", expectedUnionEnd, res!!.until)
    }

    @Test
    fun overlappingInterventionSchedules_latestStartInstantWins() {
        val mondayMask = 1 shl 0
        // Sched A: 09:00 - 17:00 (start 09:00)
        val schedA = CompiledSchedule(
            id = 100L,
            name = "Work All Day",
            weekdayMask = mondayMask,
            startMinute = 9 * 60,
            endMinute = 17 * 60,
            enabled = true,
            type = ScheduleType.INTERVENTION,
            targetPackages = setOf(targetPackage)
        )
        // Sched B: 12:00 - 14:00 (start 12:00)
        val schedB = CompiledSchedule(
            id = 200L,
            name = "Lunch Deep Work",
            weekdayMask = mondayMask,
            startMinute = 12 * 60,
            endMinute = 14 * 60,
            enabled = true,
            type = ScheduleType.INTERVENTION,
            targetPackages = setOf(targetPackage)
        )

        val at1230 = ZonedDateTime.of(2026, 9, 7, 12, 30, 0, 0, zoneId).toInstant()
        val winner = ScheduleResolver.resolveActiveInterventionSchedule(
            listOf(schedA, schedB),
            targetPackage,
            at1230,
            zoneId
        )
        assertNotNull(winner)
        assertEquals("Latest start instant must win", 200L, winner!!.id)
    }
}
