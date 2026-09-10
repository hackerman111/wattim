package io.ronesec.domain.policy

import io.ronesec.domain.model.BackoffConfig
import kotlin.math.pow
import kotlin.math.roundToLong

object Backoff {
    const val MIN_DELAY_MS = 1_000L      // 1s
    const val MAX_DELAY_MS = 300_000L    // 300s (Android 1-300s cap)
    const val CALCULATOR_COUNT = 10      // First 10 attempts for calculator (N=0..9)

    /**
     * Computes exponential backoff delay in milliseconds.
     * Formula: clamp(round(Tbase * (1 + r/100)^N), 1000, 300000)
     *
     * @param baseDurationMs Tbase in milliseconds.
     * @param growthPercent Growth rate r in percent (1..200).
     * @param priorEntriesCount N: count of prior genuine enabled-target ENTRY attempts within rolling window.
     */
    fun calculateDelayMs(
        baseDurationMs: Long,
        growthPercent: Int,
        priorEntriesCount: Int
    ): Long {
        if (priorEntriesCount <= 0) {
            return baseDurationMs.coerceIn(MIN_DELAY_MS, MAX_DELAY_MS)
        }

        val r = growthPercent.coerceIn(1, 200).toDouble() / 100.0
        val multiplier = 1.0 + r

        // Saturation check to prevent double overflow: (1 + r)^N
        // If multiplier^N > (MAX_DELAY_MS / baseDurationMs), result will saturate
        val maxRatio = MAX_DELAY_MS.toDouble() / baseDurationMs.coerceAtLeast(MIN_DELAY_MS).toDouble()
        val factor = multiplier.pow(priorEntriesCount.toDouble())

        if (factor >= maxRatio || factor.isInfinite() || factor.isNaN()) {
            return MAX_DELAY_MS
        }

        val calculated = (baseDurationMs.toDouble() * factor).roundToLong()
        return calculated.coerceIn(MIN_DELAY_MS, MAX_DELAY_MS)
    }

    /**
     * Resolves effective duration applying backoff config and prior entries count.
     */
    fun resolveEffectiveDurationMs(
        baseDurationMs: Long,
        config: BackoffConfig,
        priorEntriesCount: Int
    ): Long {
        return if (config.enabled) {
            calculateDelayMs(baseDurationMs, config.percent, priorEntriesCount)
        } else {
            baseDurationMs.coerceIn(MIN_DELAY_MS, MAX_DELAY_MS)
        }
    }

    /**
     * Computes the first 10 delays (N = 0..9) for UI calculator.
     */
    fun calculateFirstTen(baseDurationMs: Long, growthPercent: Int): List<Long> {
        return (0 until CALCULATOR_COUNT).map { n ->
            calculateDelayMs(baseDurationMs, growthPercent, n)
        }
    }

    /**
     * Counts genuine enabled-target prior ENTRY attempts in the half-open rolling window:
     * (nowEpochMs - windowMs, nowEpochMs]
     */
    fun countEligiblePriorEntries(
        entryTimestamps: List<Long>,
        nowEpochMs: Long,
        windowMs: Long
    ): Int {
        val windowStart = nowEpochMs - windowMs
        return entryTimestamps.count { ts ->
            ts > windowStart && ts <= nowEpochMs
        }
    }
}
