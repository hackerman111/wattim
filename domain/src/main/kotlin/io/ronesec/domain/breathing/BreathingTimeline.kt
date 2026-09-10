package io.ronesec.domain.breathing

import kotlin.math.ceil
import kotlin.math.max

enum class BreathingPhase {
    INHALE,
    EXHALE,
    COMPLETE;

    val displayRussian: String
        get() = when (this) {
            INHALE -> "Вдох"
            EXHALE -> "Выдох"
            COMPLETE -> "Готово"
        }
}

/**
 * Snapshot of breathing progress calculated deterministically from monotonic time.
 *
 * @property elapsedMs Elapsed time in milliseconds since timeline start: e = max(0, elapsedNow - start).
 * @property durationMs Effective breathing duration in milliseconds.
 * @property t Normalized progress clamped to [0.0, 1.0].
 * @property p Smoothstep breathing amplitude in [0.0, 1.0], turnaround at t = 0.5.
 * @property phase Current phase: INHALE (t < 0.5), EXHALE (0.5 <= t < 1.0), COMPLETE (e >= durationMs).
 * @property isComplete True when elapsedMs >= durationMs.
 * @property remainingSeconds Exact seconds remaining rounded up to tenths: ceil(max(0, d - e) / 100) / 10.
 * @property formattedCountdown Formatted countdown string "SS.S".
 */
data class BreathingProgress(
    val elapsedMs: Long,
    val durationMs: Long,
    val t: Double,
    val p: Double,
    val phase: BreathingPhase,
    val isComplete: Boolean,
    val remainingSeconds: Double,
    val formattedCountdown: String
)

object BreathingTimeline {

    /**
     * Calculates deterministic breathing progress for given monotonic timestamps.
     *
     * @param startElapsedMs Monotonic start timestamp in milliseconds (when overlay attached).
     * @param nowElapsedMs Current monotonic timestamp in milliseconds.
     * @param durationMs Effective breathing duration in milliseconds (d > 0).
     */
    fun calculate(
        startElapsedMs: Long,
        nowElapsedMs: Long,
        durationMs: Long
    ): BreathingProgress {
        val d = max(1L, durationMs)
        val e = max(0L, nowElapsedMs - startElapsedMs)
        val isComplete = e >= d

        val t = (e.toDouble() / d.toDouble()).coerceIn(0.0, 1.0)
        val phase = when {
            isComplete -> BreathingPhase.COMPLETE
            t < 0.5 -> BreathingPhase.INHALE
            else -> BreathingPhase.EXHALE
        }

        // q = 2t for t < 0.5 else 2(1 - t)
        val q = if (t < 0.5) 2.0 * t else 2.0 * (1.0 - t)
        // Smoothstep: p = 3q^2 - 2q^3. If complete, hold 0 amplitude
        val p = if (isComplete) 0.0 else (3.0 * q * q - 2.0 * q * q * q).coerceIn(0.0, 1.0)

        // Countdown: ceil(max(0, d - e) / 100) / 10.0
        val remainingMs = max(0L, d - e)
        val tenths = ceil(remainingMs.toDouble() / 100.0).toLong()
        val remainingSeconds = tenths / 10.0

        val secondsInt = tenths / 10
        val tenthPart = tenths % 10
        val formattedCountdown = String.format("%02d.%d", secondsInt, tenthPart)

        return BreathingProgress(
            elapsedMs = e,
            durationMs = d,
            t = t,
            p = p,
            phase = phase,
            isComplete = isComplete,
            remainingSeconds = remainingSeconds,
            formattedCountdown = formattedCountdown
        )
    }
}
