package io.ronesec.domain.policy

import io.ronesec.domain.model.BackoffConfig
import io.ronesec.domain.model.GlobalPause
import io.ronesec.domain.model.RuntimePolicySnapshot
import io.ronesec.domain.model.RuntimeState
import io.ronesec.domain.model.TargetConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class RandomDurationPolicyTest {

    private val zoneId = ZoneId.of("UTC")
    private val nowWall = Instant.parse("2026-09-11T12:00:00Z")
    private val nowElapsedMs = 1_000_000L
    private val targetPackage = "com.test.random"

    @Test
    fun targetConfigValidation_rejectsNegativeMaxDuration() {
        assertThrows(IllegalArgumentException::class.java) {
            TargetConfig(
                packageName = targetPackage,
                displayName = "Random Target",
                durationMs = 10_000L,
                randomDurationEnabled = true,
                randomMaxDurationMs = -1_000L
            )
        }
    }

    @Test
    fun targetConfigValidation_allowsRandomAdditionLessThanBaseDuration() {
        val config = TargetConfig(
            packageName = targetPackage,
            displayName = "Random Target",
            durationMs = 10_000L,
            randomDurationEnabled = true,
            randomMaxDurationMs = 5_000L
        )
        assertTrue(config.randomDurationEnabled)
        assertEquals(5_000L, config.randomMaxDurationMs)
    }

    @Test
    fun ruleEngine_disabledRandom_usesBaseDuration() {
        val target = TargetConfig(
            packageName = targetPackage,
            displayName = "Target",
            durationMs = 8_000L,
            randomDurationEnabled = false,
            randomMaxDurationMs = 20_000L
        )
        val snapshot = RuntimePolicySnapshot(
            revision = 1L,
            targets = mapOf(targetPackage to target),
            activeGrants = emptyMap(),
            activeBlockSessions = emptyList(),
            activeSchedules = emptyList(),
            globalPause = GlobalPause.None
        )
        val decision = RuleEngine.evaluate(
            packageName = targetPackage,
            nowWall = nowWall,
            nowElapsedMs = nowElapsedMs,
            zoneId = zoneId,
            runtimeState = RuntimeState(snapshot)
        )

        assertTrue(decision is Decision.Intervention)
        val intervention = decision as Decision.Intervention
        assertEquals(8_000L, intervention.config.durationMs)
    }

    @Test
    fun ruleEngine_enabledRandom_samplesAdditionFromZeroToMax() {
        val target = TargetConfig(
            packageName = targetPackage,
            displayName = "Target",
            durationMs = 5_000L,
            randomDurationEnabled = true,
            randomMaxDurationMs = 10_000L
        )
        val snapshot = RuntimePolicySnapshot(
            revision = 1L,
            targets = mapOf(targetPackage to target),
            activeGrants = emptyMap(),
            activeBlockSessions = emptyList(),
            activeSchedules = emptyList(),
            globalPause = GlobalPause.None
        )

        // Test with provider returning 0 addition
        val minDecision = RuleEngine.evaluate(
            packageName = targetPackage,
            nowWall = nowWall,
            nowElapsedMs = nowElapsedMs,
            zoneId = zoneId,
            runtimeState = RuntimeState(snapshot),
            randomDurationProvider = { min, _ -> min }
        ) as Decision.Intervention
        assertEquals(5_000L, minDecision.config.durationMs)

        // Test with provider returning max addition (10_000)
        val maxDecision = RuleEngine.evaluate(
            packageName = targetPackage,
            nowWall = nowWall,
            nowElapsedMs = nowElapsedMs,
            zoneId = zoneId,
            runtimeState = RuntimeState(snapshot),
            randomDurationProvider = { _, max -> max }
        ) as Decision.Intervention
        assertEquals(15_000L, maxDecision.config.durationMs)

        // Test with provider returning intermediate addition (4_000)
        val midDecision = RuleEngine.evaluate(
            packageName = targetPackage,
            nowWall = nowWall,
            nowElapsedMs = nowElapsedMs,
            zoneId = zoneId,
            runtimeState = RuntimeState(snapshot),
            randomDurationProvider = { _, _ -> 4_000L }
        ) as Decision.Intervention
        assertEquals(9_000L, midDecision.config.durationMs)
    }

    @Test
    fun ruleEngine_enabledRandom_appliesBackoffToBaseAndAddsRandom() {
        val target = TargetConfig(
            packageName = targetPackage,
            displayName = "Target",
            durationMs = 10_000L,
            randomDurationEnabled = true,
            randomMaxDurationMs = 5_000L,
            growthConfig = BackoffConfig(enabled = true, percent = 50, windowMs = 3600_000L)
        )
        val snapshot = RuntimePolicySnapshot(
            revision = 1L,
            targets = mapOf(targetPackage to target),
            activeGrants = emptyMap(),
            activeBlockSessions = emptyList(),
            activeSchedules = emptyList(),
            globalPause = GlobalPause.None
        )

        // With 1 prior entry (+50%): base 10_000 * 1.5 = 15_000L.
        // Random addition is 3_000L -> Total = 18_000L
        val decision = RuleEngine.evaluate(
            packageName = targetPackage,
            nowWall = nowWall,
            nowElapsedMs = nowElapsedMs,
            zoneId = zoneId,
            runtimeState = RuntimeState(snapshot),
            priorEntryCountOverride = 1,
            randomDurationProvider = { _, _ -> 3_000L }
        ) as Decision.Intervention

        assertEquals(18_000L, decision.config.durationMs)
        assertEquals(10_000L, decision.config.baseDurationMs)
    }
}
