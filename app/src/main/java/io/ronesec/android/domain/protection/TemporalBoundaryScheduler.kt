package io.ronesec.android.domain.protection

import io.ronesec.android.domain.engine.RuntimeState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant

class TemporalBoundaryScheduler(
    private val scope: CoroutineScope,
    private val wallClock: WallClock = SystemWallClock,
    private val onBoundaryReached: (sessionId: SessionId, targetPackage: String, boundaryType: BoundaryType, timestamp: Instant) -> Unit
) {
    private var activeJob: Job? = null
    private var activeSessionId: SessionId? = null

    fun schedule(
        sessionId: SessionId,
        targetPackage: String,
        boundaryType: BoundaryType,
        delayMs: Long
    ) {
        cancel(activeSessionId ?: SessionId.NONE)
        if (delayMs <= 0L) return

        activeSessionId = sessionId
        activeJob = scope.launch {
            delay(delayMs)
            if (activeSessionId == sessionId) {
                onBoundaryReached(sessionId, targetPackage, boundaryType, wallClock.now())
            }
        }
    }

    fun scheduleNearest(
        sessionId: SessionId,
        targetPackage: String,
        state: RuntimeState,
        preferredDelayMs: Long?,
        preferredBoundary: BoundaryType
    ) {
        val now = wallClock.now()
        val nowMs = now.toEpochMilli()
        var minDelayMs = preferredDelayMs?.takeIf { it > 0 } ?: Long.MAX_VALUE
        var chosenBoundary = preferredBoundary

        // Check global pause expiry
        val pausedUntil = state.protectionPausedUntil
        if (pausedUntil != null && pausedUntil > nowMs) {
            val pauseRemaining = pausedUntil - nowMs
            if (pauseRemaining in 1 until minDelayMs) {
                minDelayMs = pauseRemaining
                chosenBoundary = BoundaryType.GLOBAL_PAUSE_EXPIRY
            }
        }

        // Check active hard-block sessions
        for (session in state.activeBlockSessions) {
            if (session.active && session.packages.contains(targetPackage)) {
                val remaining = session.endTime.toEpochMilli() - nowMs
                if (remaining in 1 until minDelayMs) {
                    minDelayMs = remaining
                    chosenBoundary = BoundaryType.SCHEDULE_BOUNDARY
                }
            }
        }

        if (minDelayMs != Long.MAX_VALUE) {
            schedule(sessionId, targetPackage, chosenBoundary, minDelayMs)
        }
    }

    fun cancel(sessionId: SessionId) {
        if (activeSessionId == null || activeSessionId == sessionId || sessionId == SessionId.NONE) {
            activeJob?.cancel()
            activeJob = null
            activeSessionId = null
        }
    }
}
