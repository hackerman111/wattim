package io.ronesec.android.domain

import io.ronesec.android.domain.model.AnimationType
import io.ronesec.android.domain.model.InterventionConfig
import org.junit.Assert.assertEquals
import org.junit.Test

class ReinterventionExponentialTest {

    private fun calculateNextReinterventionDuration(config: InterventionConfig): Long {
        return if (config.exponentialGrowthEnabled) {
            (config.durationMs * (1.0 + config.growthPercent / 100.0))
                .toLong()
                .coerceIn(1_000L, 300_000L)
        } else {
            config.durationMs
        }
    }

    @Test
    fun testReinterventionExponentialScalingSequence() {
        val baseConfig = InterventionConfig(
            phrase = "Дышите",
            animation = AnimationType.FILL,
            durationMs = 10_000L,
            reinterventionMs = 120_000L, // 2 minutes
            quickReturnGraceMs = 0L,
            exponentialGrowthEnabled = true,
            growthPercent = 20,
            growthPeriodMinutes = 60
        )

        // Cycle 1: 10s * 1.2 = 12s
        val cycle1Duration = calculateNextReinterventionDuration(baseConfig)
        assertEquals(12_000L, cycle1Duration)

        // Cycle 2: 12s * 1.2 = 14.4s
        val configAfterCycle1 = baseConfig.copy(durationMs = cycle1Duration)
        val cycle2Duration = calculateNextReinterventionDuration(configAfterCycle1)
        assertEquals(14_400L, cycle2Duration)

        // Cycle 3: 14.4s * 1.2 = 17.28s
        val configAfterCycle2 = baseConfig.copy(durationMs = cycle2Duration)
        val cycle3Duration = calculateNextReinterventionDuration(configAfterCycle2)
        assertEquals(17_280L, cycle3Duration)

        // Cycle 4: 17.28s * 1.2 = 20.736s
        val configAfterCycle3 = baseConfig.copy(durationMs = cycle3Duration)
        val cycle4Duration = calculateNextReinterventionDuration(configAfterCycle3)
        assertEquals(20_736L, cycle4Duration)

        // Cycle 5: 20.736s * 1.2 = 24.8832s
        val configAfterCycle4 = baseConfig.copy(durationMs = cycle4Duration)
        val cycle5Duration = calculateNextReinterventionDuration(configAfterCycle4)
        assertEquals(24_883L, cycle5Duration)
    }

    @Test
    fun testReinterventionExponentialDisabledKeepsSameDuration() {
        val baseConfig = InterventionConfig(
            phrase = "Дышите",
            animation = AnimationType.FILL,
            durationMs = 10_000L,
            reinterventionMs = 120_000L,
            quickReturnGraceMs = 0L,
            exponentialGrowthEnabled = false,
            growthPercent = 20,
            growthPeriodMinutes = 60
        )

        val cycle1Duration = calculateNextReinterventionDuration(baseConfig)
        assertEquals(10_000L, cycle1Duration)
    }
}
