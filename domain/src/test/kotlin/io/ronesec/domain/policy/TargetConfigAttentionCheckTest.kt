package io.ronesec.domain.policy

import io.ronesec.domain.model.GlobalPause
import io.ronesec.domain.model.RuntimePolicySnapshot
import io.ronesec.domain.model.RuntimeState
import io.ronesec.domain.model.TargetConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class TargetConfigAttentionCheckTest {

    private val zoneId = ZoneId.of("UTC")
    private val nowWall = Instant.parse("2026-09-11T12:00:00Z")
    private val nowElapsedMs = 1_000_000L
    private val targetPackage = "com.test.attention"

    @Test
    fun targetConfig_defaultAttentionCheckValues() {
        val config = TargetConfig(
            packageName = targetPackage,
            displayName = "Attention Target"
        )
        assertFalse(config.attentionChecksEnabled)
        assertEquals(1, config.attentionCheckCount)
        assertEquals(4, config.attentionCheckCodeLength)
        assertEquals(5_000L, config.attentionCheckTimeoutMs)
    }

    @Test
    fun targetConfig_validAttentionCheckValues() {
        val config = TargetConfig(
            packageName = targetPackage,
            displayName = "Attention Target",
            attentionChecksEnabled = true,
            attentionCheckCount = 3,
            attentionCheckCodeLength = 6,
            attentionCheckTimeoutMs = 10_000L
        )
        assertTrue(config.attentionChecksEnabled)
        assertEquals(3, config.attentionCheckCount)
        assertEquals(6, config.attentionCheckCodeLength)
        assertEquals(10_000L, config.attentionCheckTimeoutMs)
    }

    @Test
    fun targetConfig_rejectsInvalidCheckCount() {
        assertThrows(IllegalArgumentException::class.java) {
            TargetConfig(
                packageName = targetPackage,
                displayName = "Target",
                attentionCheckCount = 0
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            TargetConfig(
                packageName = targetPackage,
                displayName = "Target",
                attentionCheckCount = 6
            )
        }
    }

    @Test
    fun targetConfig_rejectsInvalidCodeLength() {
        assertThrows(IllegalArgumentException::class.java) {
            TargetConfig(
                packageName = targetPackage,
                displayName = "Target",
                attentionCheckCodeLength = 2
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            TargetConfig(
                packageName = targetPackage,
                displayName = "Target",
                attentionCheckCodeLength = 9
            )
        }
    }

    @Test
    fun targetConfig_rejectsInvalidTimeout() {
        assertThrows(IllegalArgumentException::class.java) {
            TargetConfig(
                packageName = targetPackage,
                displayName = "Target",
                attentionCheckTimeoutMs = 2_000L
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            TargetConfig(
                packageName = targetPackage,
                displayName = "Target",
                attentionCheckTimeoutMs = 35_000L
            )
        }
    }

    @Test
    fun ruleEngine_propagatesAttentionCheckToEffectiveConfig() {
        val target = TargetConfig(
            packageName = targetPackage,
            displayName = "Target",
            attentionChecksEnabled = true,
            attentionCheckCount = 2,
            attentionCheckCodeLength = 5,
            attentionCheckTimeoutMs = 7_000L
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
        ) as Decision.Intervention

        assertTrue(decision.config.attentionChecksEnabled)
        assertEquals(2, decision.config.attentionCheckCount)
        assertEquals(5, decision.config.attentionCheckCodeLength)
        assertEquals(7_000L, decision.config.attentionCheckTimeoutMs)
    }
}
