package io.ronesec.android.domain

import io.ronesec.android.data.local.entity.BlockScheduleEntity
import io.ronesec.android.data.local.entity.TargetAppEntity
import io.ronesec.android.domain.model.AnimationType
import io.ronesec.android.domain.model.BlockSchedule
import io.ronesec.android.domain.model.InterventionConfig
import io.ronesec.android.domain.model.ScheduleAppOverride
import io.ronesec.android.domain.model.ScheduleType
import io.ronesec.android.domain.model.TargetApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalTime

class ScheduleOverrideTest {

    @Test
    fun testBlockScheduleEntitySerializationWithOverrides() {
        val overrides = mapOf(
            "com.instagram.android" to ScheduleAppOverride(durationMs = 15000L, reinterventionMs = 600000L),
            "com.twitter.android" to ScheduleAppOverride(durationMs = 12000L, reinterventionMs = null)
        )
        val domain = BlockSchedule(
            id = 1L,
            name = "Work Focus",
            days = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY),
            start = LocalTime.of(9, 0),
            end = LocalTime.of(17, 0),
            packages = setOf("com.instagram.android", "com.twitter.android"),
            enabled = true,
            scheduleType = ScheduleType.INTERVENTION,
            appOverrides = overrides
        )

        val entity = BlockScheduleEntity.fromDomain(domain)
        assertEquals("INTERVENTION", entity.scheduleType)
        assertTrue(entity.appOverridesJson != null)

        val reconstructed = entity.toDomain()
        assertEquals(ScheduleType.INTERVENTION, reconstructed.scheduleType)
        assertEquals(2, reconstructed.appOverrides.size)
        assertEquals(15000L, reconstructed.appOverrides["com.instagram.android"]?.durationMs)
        assertEquals(600000L, reconstructed.appOverrides["com.instagram.android"]?.reinterventionMs)
        assertEquals(12000L, reconstructed.appOverrides["com.twitter.android"]?.durationMs)
        assertNull(reconstructed.appOverrides["com.twitter.android"]?.reinterventionMs)
    }

    @Test
    fun testBlockScheduleEntitySerializationDefaults() {
        val domain = BlockSchedule(
            id = 2L,
            name = "Hard Block Night",
            days = setOf(DayOfWeek.SATURDAY),
            start = LocalTime.of(22, 0),
            end = LocalTime.of(6, 0),
            packages = setOf("com.instagram.android"),
            enabled = true
        )

        val entity = BlockScheduleEntity.fromDomain(domain)
        assertEquals("HARD_BLOCK", entity.scheduleType)
        assertNull(entity.appOverridesJson)

        val reconstructed = entity.toDomain()
        assertEquals(ScheduleType.HARD_BLOCK, reconstructed.scheduleType)
        assertTrue(reconstructed.appOverrides.isEmpty())
    }

    @Test
    fun testTargetAppEntityMappingWithExponentialGrowth() {
        val config = InterventionConfig(
            phrase = "Pause",
            animation = AnimationType.FILL,
            durationMs = 10000L,
            reinterventionMs = 120000L,
            quickReturnGraceMs = 5000L,
            exponentialGrowthEnabled = true,
            growthPercent = 25,
            growthPeriodMinutes = 45
        )
        val targetApp = TargetApp(
            packageName = "com.reddit.frontpage",
            displayName = "Reddit",
            enabled = true,
            intervention = config
        )

        val entity = TargetAppEntity.fromDomain(targetApp)
        assertTrue(entity.exponentialGrowthEnabled)
        assertEquals(25, entity.growthPercent)
        assertEquals(45, entity.growthPeriodMinutes)

        val reconstructed = entity.toDomain()
        assertTrue(reconstructed.intervention.exponentialGrowthEnabled)
        assertEquals(25, reconstructed.intervention.growthPercent)
        assertEquals(45, reconstructed.intervention.growthPeriodMinutes)
        assertEquals(10000L, reconstructed.intervention.durationMs)
    }

    @Test
    fun testTargetAppEntityDefaultExponentialGrowth() {
        val targetApp = TargetApp(
            packageName = "com.default.app",
            displayName = "Default App"
        )
        assertFalse(targetApp.intervention.exponentialGrowthEnabled)
        assertEquals(20, targetApp.intervention.growthPercent)
        assertEquals(60, targetApp.intervention.growthPeriodMinutes)

        val entity = TargetAppEntity.fromDomain(targetApp)
        assertFalse(entity.exponentialGrowthEnabled)
        assertEquals(20, entity.growthPercent)
        assertEquals(60, entity.growthPeriodMinutes)

        val reconstructed = entity.toDomain()
        assertFalse(reconstructed.intervention.exponentialGrowthEnabled)
        assertEquals(20, reconstructed.intervention.growthPercent)
        assertEquals(60, reconstructed.intervention.growthPeriodMinutes)
    }
}
