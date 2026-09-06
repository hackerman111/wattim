package io.ronesec.android.domain.animation

import io.ronesec.android.domain.model.AnimationPhase
import kotlin.math.max

class FillAnimation : InterventionAnimation {

    private var durationMs: Long = 8_000L
    private var startTimeMs: Long = 0L
    private var isRunning: Boolean = false

    override fun start(durationMs: Long, startTimeMs: Long) {
        this.durationMs = max(1_000L, durationMs)
        this.startTimeMs = startTimeMs
        this.isRunning = true
    }

    override fun progress(currentTimeMs: Long): Float {
        if (!isRunning) return 0f
        val elapsed = (currentTimeMs - startTimeMs).coerceAtLeast(0L)
        val t = (elapsed.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        return fillProgress(t)
    }

    override fun easedProgress(currentTimeMs: Long): Float {
        val raw = progress(currentTimeMs)
        // Smoothstep interpolation (Hermite cubic curve): S(x) = 3x^2 - 2x^3
        // Guarantees zero derivative at 0 and 1, perfectly smooth turnaround without bounce/overshoot
        return raw * raw * (3f - 2f * raw)
    }

    override fun phase(currentTimeMs: Long): AnimationPhase {
        if (!isRunning) return AnimationPhase.COMPLETE
        val elapsed = currentTimeMs - startTimeMs
        if (elapsed >= durationMs) return AnimationPhase.COMPLETE
        val t = (elapsed.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        return if (t < 0.5f) AnimationPhase.INHALE else AnimationPhase.EXHALE
    }

    override fun isFinished(currentTimeMs: Long): Boolean {
        if (!isRunning) return true
        return (currentTimeMs - startTimeMs) >= durationMs
    }

    override fun remainingTimeMs(currentTimeMs: Long): Long {
        if (!isRunning) return 0L
        val elapsed = currentTimeMs - startTimeMs
        return max(0L, durationMs - elapsed)
    }

    override fun stop() {
        isRunning = false
    }

    companion object {
        fun fillProgress(t: Float): Float {
            val clamped = t.coerceIn(0f, 1f)
            return if (clamped <= 0.5f) {
                clamped * 2f
            } else {
                (1f - clamped) * 2f
            }
        }
    }
}
