package io.ronesec.android.platform.time

import android.os.SystemClock
import io.ronesec.domain.model.MonotonicClock
import io.ronesec.domain.model.WallClock
import java.time.Instant
import java.time.ZoneId

class AndroidWallClock : WallClock {
    override fun now(): Instant = Instant.now()
    override fun zoneId(): ZoneId = ZoneId.systemDefault()
}

class AndroidMonotonicClock : MonotonicClock {
    override fun elapsedRealtimeMs(): Long = SystemClock.elapsedRealtime()
}
