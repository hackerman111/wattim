package io.ronesec.android.domain

import io.ronesec.android.domain.engine.RuleEngine
import io.ronesec.android.domain.engine.RuntimeState
import io.ronesec.android.domain.model.AccessGrant
import io.ronesec.android.domain.model.BlockSchedule
import io.ronesec.android.domain.model.BlockSession
import io.ronesec.android.domain.model.Decision
import io.ronesec.android.domain.model.InterventionConfig
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
}
