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
    fun targetConfigValidation_rejectsMaxLessThanMinWhenEnabled() {
        assertThrows(IllegalArgumentException::class.java) {
            TargetConfig(
                packageName = targetPackage,
                displayName = "Random Target",
                durationMs = 10_000L,
                randomDurationEnabled = true,
                randomMaxDurationMs = 5_000L
            )
        }
    }

    @Test
    fun targetConfigValidation_allowsValidRandomRange() {
        val config = TargetConfig(
            packageName = targetPackage,
            displayName = "Random Target",
            durationMs = 5_000L,
            randomDurationEnabled = true,
            randomMaxDurationMs = 15_000L
        )
        assertTrue(config.randomDurationEnabled)
        assertEquals(15_000L, config.randomMaxDurationMs)
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
    fun ruleEngine_enabledRandom_samplesWithinRange() {
        val target = TargetConfig(
            packageName = targetPackage,
            displayName = "Target",
            durationMs = 5_000L,
            randomDurationEnabled = true,
            randomMaxDurationMs = 15_000L
        )
        val snapshot = RuntimePolicySnapshot(
            revision = 1L,
            targets = mapOf(targetPackage to target),
            activeGrants = emptyMap(),
            activeBlockSessions = emptyList(),
            activeSchedules = emptyList(),
            globalPause = GlobalPause.None
        )

        // Test with provider returning min
        val minDecision = RuleEngine.evaluate(
            packageName = targetPackage,
            nowWall = nowWall,
            nowElapsedMs = nowElapsedMs,
            zoneId = zoneId,
            runtimeState = RuntimeState(snapshot),
            randomDurationProvider = { min, _ -> min }
        ) as Decision.Intervention
        assertEquals(5_000L, minDecision.config.durationMs)

        // Test with provider returning max
        val maxDecision = RuleEngine.evaluate(
            packageName = targetPackage,
            nowWall = nowWall,
            nowElapsedMs = nowElapsedMs,
            zoneId = zoneId,
            runtimeState = RuntimeState(snapshot),
            randomDurationProvider = { _, max -> max }
        ) as Decision.Intervention
        assertEquals(15_000L, maxDecision.config.durationMs)

        // Test with provider returning intermediate value
        val midDecision = RuleEngine.evaluate(
            packageName = targetPackage,
            nowWall = nowWall,
            nowElapsedMs = nowElapsedMs,
            zoneId = zoneId,
            runtimeState = RuntimeState(snapshot),
            randomDurationProvider = { min, max -> (min + max) / 2 }
        ) as Decision.Intervention
        assertEquals(10_000L, midDecision.config.durationMs)
    }

    @Test
    fun ruleEngine_enabledRandom_appliesBackoffOnSampledBase() {
        val target = TargetConfig(
            packageName = targetPackage,
            displayName = "Target",
            durationMs = 10_000L,
            randomDurationEnabled = true,
            randomMaxDurationMs = 20_000L,
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

        // With provider returning 10_000L and 1 prior entry (+50%): 10_000 * 1.5 = 15_000L
        val decision = RuleEngine.evaluate(
            packageName = targetPackage,
            nowWall = nowWall,
            nowElapsedMs = nowElapsedMs,
            zoneId = zoneId,
            runtimeState = RuntimeState(snapshot),
            priorEntryCountOverride = 1,
            randomDurationProvider = { min, _ -> min }
        ) as Decision.Intervention

        assertEquals(15_000L, decision.config.durationMs)
    }
}
