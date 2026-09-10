package io.ronesec.domain.model

import java.time.Instant
import java.time.ZoneId

/**
 * Wall clock providing wall time and zone context.
 * Used for schedules, calendar intervals, stored deadlines, and statistics.
 */
interface WallClock {
    fun now(): Instant
    fun zoneId(): ZoneId
}

/**
 * Monotonic clock providing elapsed realtime milliseconds.
 * Used for breathing timelines, session repeats, Quick Return grace, and frame intervals.
 * Never adjusted by system time/timezone shifts.
 */
interface MonotonicClock {
    fun elapsedRealtimeMs(): Long
}
