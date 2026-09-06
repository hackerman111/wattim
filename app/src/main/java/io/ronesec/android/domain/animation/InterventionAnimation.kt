package io.ronesec.android.domain.animation

import io.ronesec.android.domain.model.AnimationPhase

interface InterventionAnimation {
    fun start(durationMs: Long, startTimeMs: Long = System.currentTimeMillis())
    fun progress(currentTimeMs: Long = System.currentTimeMillis()): Float
    fun easedProgress(currentTimeMs: Long = System.currentTimeMillis()): Float
    fun phase(currentTimeMs: Long = System.currentTimeMillis()): AnimationPhase
    fun isFinished(currentTimeMs: Long = System.currentTimeMillis()): Boolean
    fun remainingTimeMs(currentTimeMs: Long = System.currentTimeMillis()): Long
    fun stop()
}
