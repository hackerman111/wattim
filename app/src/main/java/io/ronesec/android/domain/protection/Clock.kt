package io.ronesec.android.domain.protection

import android.os.SystemClock
import java.time.Instant
import java.time.ZoneId

interface WallClock {
    fun now(): Instant
    fun zoneId(): ZoneId = ZoneId.systemDefault()
}

interface MonotonicClock {
    fun elapsedRealtimeMillis(): Long
}

object SystemWallClock : WallClock {
    override fun now(): Instant = Instant.now()
    override fun zoneId(): ZoneId = ZoneId.systemDefault()
}

object SystemMonotonicClock : MonotonicClock {
    override fun elapsedRealtimeMillis(): Long = SystemClock.elapsedRealtime()
}

class FakeWallClock(
    private var currentInstant: Instant = Instant.EPOCH,
    private var currentZoneId: ZoneId = ZoneId.of("UTC")
) : WallClock {
    override fun now(): Instant = currentInstant
    override fun zoneId(): ZoneId = currentZoneId

    fun set(instant: Instant) {
        currentInstant = instant
    }

    fun advance(durationMs: Long) {
        currentInstant = currentInstant.plusMillis(durationMs)
    }

    fun setZone(zoneId: ZoneId) {
        currentZoneId = zoneId
    }
}

class FakeMonotonicClock(
    private var currentMillis: Long = 0L
) : MonotonicClock {
    override fun elapsedRealtimeMillis(): Long = currentMillis

    fun advance(durationMs: Long) {
        currentMillis += durationMs
    }

    fun set(millis: Long) {
        currentMillis = millis
    }
}
