package io.ronesec.domain.model

import java.time.Instant
import java.time.ZoneId

class FakeWallClock(
    var currentInstant: Instant = Instant.parse("2026-09-09T12:00:00Z"),
    var currentZoneId: ZoneId = ZoneId.of("UTC")
) : WallClock {
    override fun now(): Instant = currentInstant
    override fun zoneId(): ZoneId = currentZoneId

    fun advanceSeconds(seconds: Long) {
        currentInstant = currentInstant.plusSeconds(seconds)
    }

    fun advanceMillis(millis: Long) {
        currentInstant = currentInstant.plusMillis(millis)
    }
}

class FakeMonotonicClock(
    var currentElapsedMs: Long = 1_000_000L
) : MonotonicClock {
    override fun elapsedRealtimeMs(): Long = currentElapsedMs

    fun advanceMillis(millis: Long) {
        currentElapsedMs += millis
    }
}
