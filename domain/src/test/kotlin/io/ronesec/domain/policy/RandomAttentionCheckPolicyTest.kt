package io.ronesec.domain.policy

import io.ronesec.domain.model.GlobalPause
import io.ronesec.domain.model.RuntimePolicySnapshot
import io.ronesec.domain.model.RuntimeState
import io.ronesec.domain.model.TargetConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class RandomAttentionCheckPolicyTest {

    private val zoneId = ZoneId.of("UTC")
    private val nowWall = Instant.parse("2026-09-11T12:00:00Z")
    private val nowElapsedMs = 1_000_000L
    private val targetPackage = "com.test.randomattention"

    @Test
    fun ruleEngine_disabledRandomAttentionCheckCount_usesFixedCount() {
        val target = TargetConfig(
            packageName = targetPackage,
            displayName = "Target",
            attentionChecksEnabled = true,
            attentionCheckCount = 3,
            attentionCheckRandomCountEnabled = false,
            attentionCheckMinCount = 1,
            attentionCheckMaxCount = 5
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
            runtimeState = RuntimeState(snapshot),
            randomAttentionCheckCountProvider = { _, _ -> 5 }
        ) as Decision.Intervention

        assertTrue(decision.config.attentionChecksEnabled)
        assertEquals(3, decision.config.attentionCheckCount)
    }

    @Test
    fun ruleEngine_enabledRandomAttentionCheckCount_samplesWithProviderMin() {
        val target = TargetConfig(
            packageName = targetPackage,
            displayName = "Target",
            attentionChecksEnabled = true,
            attentionCheckCount = 1,
            attentionCheckRandomCountEnabled = true,
            attentionCheckMinCount = 2,
            attentionCheckMaxCount = 4
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
            runtimeState = RuntimeState(snapshot),
            randomAttentionCheckCountProvider = { min, _ -> min }
        ) as Decision.Intervention

        assertTrue(decision.config.attentionChecksEnabled)
        assertEquals(2, decision.config.attentionCheckCount)
    }

    @Test
    fun ruleEngine_enabledRandomAttentionCheckCount_samplesWithProviderMax() {
        val target = TargetConfig(
            packageName = targetPackage,
            displayName = "Target",
            attentionChecksEnabled = true,
            attentionCheckCount = 1,
            attentionCheckRandomCountEnabled = true,
            attentionCheckMinCount = 2,
            attentionCheckMaxCount = 4
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
            runtimeState = RuntimeState(snapshot),
            randomAttentionCheckCountProvider = { _, max -> max }
        ) as Decision.Intervention

        assertTrue(decision.config.attentionChecksEnabled)
        assertEquals(4, decision.config.attentionCheckCount)
    }
}
