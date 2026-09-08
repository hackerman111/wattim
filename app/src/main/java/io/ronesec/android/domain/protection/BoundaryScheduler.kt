package io.ronesec.android.domain.protection

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class BoundaryScheduler(
    private val scope: CoroutineScope,
    private val onBoundaryReached: (sessionId: Long, targetPackage: String, boundaryType: BoundaryType) -> Unit
) {
    private var scheduledJob: Job? = null
    private var activeSessionId: Long? = null

    fun schedule(sessionId: Long, targetPackage: String, boundaryType: BoundaryType, delayMs: Long) {
        cancel(activeSessionId ?: -1L)
        if (delayMs <= 0L) return

        activeSessionId = sessionId
        scheduledJob = scope.launch {
            delay(delayMs)
            if (activeSessionId == sessionId) {
                onBoundaryReached(sessionId, targetPackage, boundaryType)
            }
        }
    }

    fun cancel(sessionId: Long) {
        if (activeSessionId == null || activeSessionId == sessionId) {
            scheduledJob?.cancel()
            scheduledJob = null
            activeSessionId = null
        }
    }
}
