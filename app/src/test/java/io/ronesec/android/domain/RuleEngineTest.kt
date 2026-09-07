package io.ronesec.android.domain

import io.ronesec.android.domain.engine.RuleEngine
import io.ronesec.android.domain.engine.RuntimeState
import io.ronesec.android.domain.model.AccessGrant
import io.ronesec.android.domain.model.BlockSchedule
import io.ronesec.android.domain.model.BlockSession
import io.ronesec.android.domain.model.Decision
import io.ronesec.android.domain.model.InterventionConfig
import io.ronesec.android.domain.model.ScheduleAppOverride
import io.ronesec.android.domain.model.ScheduleType
import io.ronesec.android.domain.model.TargetApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

class RuleEngineTest {

    private val ruleEngine = RuleEngine()
    private val zoneId = ZoneId.of("UTC")
    private val baseTime = Instant.parse("2026-09-07T12:00:00Z") // Monday 12:00 UTC

    private val instagramConfig = InterventionConfig(
        phrase = "Сделайте глубокий вдох",
        durationMs = 8_000L,
        reinterventionMs = 300_000L, // 5 min
        quickReturnGraceMs = 60_000L  // 60 sec
    )

    private val instagram = TargetApp(
        packageName = "com.instagram.android",
        displayName = "Instagram",
        enabled = true,
        intervention = instagramConfig
    )

    @Test
    fun `unknown package returns Allow`() {
        val state = RuntimeState(targets = mapOf(instagram.packageName to instagram))
        val decision = ruleEngine.evaluate("com.random.app", baseTime, state, zoneId)
        assertEquals(Decision.Allow, decision)
    }

    @Test
    fun `disabled protected package returns Allow`() {
        val disabledInstagram = instagram.copy(enabled = false)
        val state = RuntimeState(targets = mapOf(disabledInstagram.packageName to disabledInstagram))
        val decision = ruleEngine.evaluate(disabledInstagram.packageName, baseTime, state, zoneId)
        assertEquals(Decision.Allow, decision)
    }

    @Test
    fun `protected package requires Intervention on first launch`() {
        val state = RuntimeState(targets = mapOf(instagram.packageName to instagram))
        val decision = ruleEngine.evaluate(instagram.packageName, baseTime, state, zoneId)
        assertTrue(decision is Decision.Intervention)
        assertEquals(instagramConfig, (decision as Decision.Intervention).config)
    }

    @Test
    fun `active unexpired grant returns Allow`() {
        val grant = AccessGrant(
            packageName = instagram.packageName,
            createdAt = baseTime,
            expiresAt = baseTime.plusSeconds(300) // 5 minutes later
        )
        val state = RuntimeState(
            targets = mapOf(instagram.packageName to instagram),
            activeGrants = mapOf(instagram.packageName to grant)
        )
        val decision = ruleEngine.evaluate(instagram.packageName, baseTime.plusSeconds(120), state, zoneId)
        assertEquals(Decision.Allow, decision)
    }

    @Test
    fun `expired grant requires Intervention again - AC-09`() {
        val grant = AccessGrant(
            packageName = instagram.packageName,
            createdAt = baseTime,
            expiresAt = baseTime.plusSeconds(300) // expired at 12:05:00
        )
        val state = RuntimeState(
            targets = mapOf(instagram.packageName to instagram),
            activeGrants = mapOf(instagram.packageName to grant)
        )
        // Check at 12:05:01
        val decision = ruleEngine.evaluate(instagram.packageName, baseTime.plusSeconds(301), state, zoneId)
        assertTrue(decision is Decision.Intervention)
    }

    @Test
    fun `quick return grace period returns Allow if return delay less than grace - AC-10`() {
        val lastExit = baseTime // Exited at 12:00:00
        val state = RuntimeState(
            targets = mapOf(instagram.packageName to instagram),
            lastExitTimes = mapOf(instagram.packageName to lastExit)
        )
        // Returned 45s later (< 60s grace)
        val decision = ruleEngine.evaluate(instagram.packageName, baseTime.plusSeconds(45), state, zoneId)
        assertEquals(Decision.Allow, decision)
    }

    @Test
    fun `quick return grace period triggers Intervention if return delay reaches or exceeds grace - AC-10`() {
        val lastExit = baseTime // Exited at 12:00:00
        val state = RuntimeState(
            targets = mapOf(instagram.packageName to instagram),
            lastExitTimes = mapOf(instagram.packageName to lastExit)
        )
        // Returned 61s later (>= 60s grace)
        val decision = ruleEngine.evaluate(instagram.packageName, baseTime.plusSeconds(61), state, zoneId)
        assertTrue(decision is Decision.Intervention)
    }

    @Test
    fun `hard block session overrides active AccessGrant - AC-11`() {
        val grant = AccessGrant(
            packageName = instagram.packageName,
            createdAt = baseTime,
            expiresAt = baseTime.plusSeconds(3600)
        )
        val hardBlock = BlockSession(
            id = 1,
            name = "FOCUS SESSION",
            startTime = baseTime,
            endTime = baseTime.plusSeconds(1800), // 30 min focus
            active = true,
            packages = setOf(instagram.packageName)
        )
        val state = RuntimeState(
            targets = mapOf(instagram.packageName to instagram),
            activeGrants = mapOf(instagram.packageName to grant),
            activeBlockSessions = listOf(hardBlock)
        )

        val decision = ruleEngine.evaluate(instagram.packageName, baseTime.plusSeconds(60), state, zoneId)
        assertTrue(decision is Decision.Block)
        assertEquals(hardBlock.endTime, (decision as Decision.Block).until)
    }

    @Test
    fun `scheduled block blocks during active schedule hours`() {
        val schedule = BlockSchedule(
            id = 1,
            name = "STUDY",
            days = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY),
            start = LocalTime.of(9, 0),
            end = LocalTime.of(16, 0),
            packages = setOf(instagram.packageName),
            enabled = true
        )
        val state = RuntimeState(
            targets = mapOf(instagram.packageName to instagram),
            blockSchedules = listOf(schedule)
        )

        // Monday 12:00 is within 09:00 - 16:00
        val decision = ruleEngine.evaluate(instagram.packageName, baseTime, state, zoneId)
        assertTrue(decision is Decision.Block)
    }

    @Test
    fun `scheduled block handles overnight schedule spanning midnight`() {
        val schedule = BlockSchedule(
            id = 2,
            name = "NIGHT_FOCUS",
            days = setOf(DayOfWeek.MONDAY),
            start = LocalTime.of(22, 0),
            end = LocalTime.of(6, 0),
            packages = setOf(instagram.packageName),
            enabled = true
        )
        val state = RuntimeState(
            targets = mapOf(instagram.packageName to instagram),
            blockSchedules = listOf(schedule)
        )

        // Monday 23:30 UTC is within 22:00 - 06:00
        val lateNightTime = Instant.parse("2026-09-07T23:30:00Z")
        val decision = ruleEngine.evaluate(instagram.packageName, lateNightTime, state, zoneId)
        assertTrue(decision is Decision.Block)
    }

    @Test
    fun `quick return grace 0 triggers Intervention immediately upon return`() {
        val zeroGraceConfig = instagramConfig.copy(quickReturnGraceMs = 0L)
        val zeroGraceApp = instagram.copy(intervention = zeroGraceConfig)
        val lastExit = baseTime
        val state = RuntimeState(
            targets = mapOf(zeroGraceApp.packageName to zeroGraceApp),
            lastExitTimes = mapOf(zeroGraceApp.packageName to lastExit)
        )
        // Returned immediately (1ms later)
        val decision = ruleEngine.evaluate(zeroGraceApp.packageName, baseTime.plusMillis(1), state, zoneId)
        assertTrue(decision is Decision.Intervention)
    }

    @Test
    fun `active HARD_BLOCK schedule returns Decision Block`() {
        val schedule = BlockSchedule(
            id = 10,
            name = "WORK HARD BLOCK",
            days = setOf(DayOfWeek.MONDAY),
            start = LocalTime.of(9, 0),
            end = LocalTime.of(17, 0),
            packages = setOf(instagram.packageName),
            enabled = true,
            scheduleType = ScheduleType.HARD_BLOCK
        )
        val state = RuntimeState(
            targets = mapOf(instagram.packageName to instagram),
            blockSchedules = listOf(schedule)
        )
        val decision = ruleEngine.evaluate(instagram.packageName, baseTime, state, zoneId)
        assertTrue(decision is Decision.Block)
    }

    @Test
    fun `active INTERVENTION schedule overrides durationMs and reinterventionMs`() {
        val schedule = BlockSchedule(
            id = 11,
            name = "WORK INTERVENTION",
            days = setOf(DayOfWeek.MONDAY),
            start = LocalTime.of(9, 0),
            end = LocalTime.of(17, 0),
            packages = setOf(instagram.packageName),
            enabled = true,
            scheduleType = ScheduleType.INTERVENTION,
            appOverrides = mapOf(
                instagram.packageName to ScheduleAppOverride(
                    durationMs = 15_000L,
                    reinterventionMs = 120_000L
                )
            )
        )
        val state = RuntimeState(
            targets = mapOf(instagram.packageName to instagram),
            blockSchedules = listOf(schedule)
        )
        val decision = ruleEngine.evaluate(instagram.packageName, baseTime, state, zoneId)
        assertTrue(decision is Decision.Intervention)
        val config = (decision as Decision.Intervention).config
        assertEquals(15_000L, config.durationMs)
        assertEquals(120_000L, config.reinterventionMs)
    }

    @Test
    fun `active INTERVENTION schedule allows access if AccessGrant is active`() {
        val schedule = BlockSchedule(
            id = 12,
            name = "WORK INTERVENTION",
            days = setOf(DayOfWeek.MONDAY),
            start = LocalTime.of(9, 0),
            end = LocalTime.of(17, 0),
            packages = setOf(instagram.packageName),
            enabled = true,
            scheduleType = ScheduleType.INTERVENTION,
            appOverrides = mapOf(
                instagram.packageName to ScheduleAppOverride(
                    durationMs = 15_000L,
                    reinterventionMs = 120_000L
                )
            )
        )
        val grant = AccessGrant(
            packageName = instagram.packageName,
            createdAt = baseTime,
            expiresAt = baseTime.plusSeconds(300)
        )
        val state = RuntimeState(
            targets = mapOf(instagram.packageName to instagram),
            activeGrants = mapOf(instagram.packageName to grant),
            blockSchedules = listOf(schedule)
        )
        val decision = ruleEngine.evaluate(instagram.packageName, baseTime.plusSeconds(60), state, zoneId)
        assertEquals(Decision.Allow, decision)
    }

    @Test
    fun `active INTERVENTION schedule allows access if within quick return grace`() {
        val schedule = BlockSchedule(
            id = 13,
            name = "WORK INTERVENTION",
            days = setOf(DayOfWeek.MONDAY),
            start = LocalTime.of(9, 0),
            end = LocalTime.of(17, 0),
            packages = setOf(instagram.packageName),
            enabled = true,
            scheduleType = ScheduleType.INTERVENTION
        )
        val lastExit = baseTime
        val state = RuntimeState(
            targets = mapOf(instagram.packageName to instagram),
            lastExitTimes = mapOf(instagram.packageName to lastExit),
            blockSchedules = listOf(schedule)
        )
        val decision = ruleEngine.evaluate(instagram.packageName, baseTime.plusSeconds(30), state, zoneId)
        assertEquals(Decision.Allow, decision)
    }

    @Test
    fun `exponentialGrowthEnabled true calculates duration properly with recent attempts`() {
        val expConfig = instagramConfig.copy(
            durationMs = 10_000L,
            exponentialGrowthEnabled = true,
            growthPercent = 20
        )
        val app = instagram.copy(intervention = expConfig)
        val state = RuntimeState(targets = mapOf(app.packageName to app))

        val decision = ruleEngine.evaluate(
            packageName = app.packageName,
            now = baseTime,
            state = state,
            zoneId = zoneId,
            recentAttemptsCount = 5
        )

        assertTrue(decision is Decision.Intervention)
        val config = (decision as Decision.Intervention).config
        assertEquals(24_883L, config.durationMs)
    }

    @Test
    fun `exponentialGrowthEnabled false keeps original duration even if recentAttemptsCount greater than 0`() {
        val linearConfig = instagramConfig.copy(
            durationMs = 8_000L,
            exponentialGrowthEnabled = false,
            growthPercent = 20
        )
        val app = instagram.copy(intervention = linearConfig)
        val state = RuntimeState(targets = mapOf(app.packageName to app))

        val decision = ruleEngine.evaluate(
            packageName = app.packageName,
            now = baseTime,
            state = state,
            zoneId = zoneId,
            recentAttemptsCount = 5
        )

        assertTrue(decision is Decision.Intervention)
        val config = (decision as Decision.Intervention).config
        assertEquals(8_000L, config.durationMs)
    }

    @Test
    fun `global protection pause unexpired allows access`() {
        val pausedUntil = baseTime.toEpochMilli() + 600_000L // 10 minutes in future
        val state = RuntimeState(
            targets = mapOf(instagram.packageName to instagram),
            protectionPausedUntil = pausedUntil
        )
        val decision = ruleEngine.evaluate(instagram.packageName, baseTime, state, zoneId)
        assertEquals(Decision.Allow, decision)
    }

    @Test
    fun `global protection pause indefinite -1 allows access`() {
        val state = RuntimeState(
            targets = mapOf(instagram.packageName to instagram),
            protectionPausedUntil = -1L
        )
        val decision = ruleEngine.evaluate(instagram.packageName, baseTime, state, zoneId)
        assertEquals(Decision.Allow, decision)
    }

    @Test
    fun `global protection pause expired falls back to intervention`() {
        val pausedUntil = baseTime.toEpochMilli() - 1_000L // 1 second in past
        val state = RuntimeState(
            targets = mapOf(instagram.packageName to instagram),
            protectionPausedUntil = pausedUntil
        )
        val decision = ruleEngine.evaluate(instagram.packageName, baseTime, state, zoneId)
        assertTrue(decision is Decision.Intervention)
    }
}
