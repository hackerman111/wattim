package io.ronesec.domain.policy

import io.ronesec.domain.model.CompiledSchedule
import io.ronesec.domain.model.RuntimePolicySnapshot
import io.ronesec.domain.model.RuntimeState
import io.ronesec.domain.model.ScheduleType
import io.ronesec.domain.model.TargetConfig
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

class CodeSettingsPolicyTest {
    @Test
    fun bothPolicyPathsPreserveIndependentCodeRequirements() {
        val packageName = "sample.app"
        val schedule = CompiledSchedule(
            id = 1L, name = "Daily", weekdayMask = 127,
            startMinute = 0, endMinute = 1439, enabled = true,
            type = ScheduleType.INTERVENTION, targetPackages = setOf(packageName)
        )
        for (scheduled in listOf(false, true)) {
            for (twoStage in listOf(false, true)) {
                for (emergency in listOf(false, true)) {
                    val target = TargetConfig(
                        packageName, "Sample", twoStageUnlock = twoStage,
                        unlockCodeLength = 7, requireEmergencyCode = emergency
                    )
                    val snapshot = RuntimePolicySnapshot.EMPTY.copy(
                        targets = mapOf(packageName to target),
                        activeSchedules = if (scheduled) listOf(schedule) else emptyList()
                    )
                    val decision = RuleEngine.evaluate(
                        packageName, Instant.parse("2026-09-11T12:00:00Z"), 1_000L,
                        ZoneOffset.UTC, RuntimeState(snapshot)
                    ) as Decision.Intervention
                    assertEquals(twoStage, decision.config.twoStageUnlock)
                    assertEquals(emergency, decision.config.requireEmergencyCode)
                    assertEquals(7, decision.config.unlockCodeLength)
                }
            }
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsEmptyCodes() {
        TargetConfig("sample.app", "Sample", unlockCodeLength = 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsOverlongCodes() {
        TargetConfig("sample.app", "Sample", unlockCodeLength = 11)
    }
}
