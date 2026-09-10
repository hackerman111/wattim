package io.ronesec.android.platform.time

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Single cancellable boundary delay scheduler.
 * Manages exactly one active boundary job to the earliest current deadline:
 * repeat, grant end, grace end, pause end, manual end, schedule start/end.
 * Satisfies F28, Section 6, and Section 11.2 (T02/T05/T07).
 */
class TemporalBoundaryScheduler(
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val onBoundaryReached: suspend (boundaryToken: Long) -> Unit
) {
    private var activeJob: Job? = null
    private var currentBoundaryToken: Long? = null
    private val mutex = Mutex()

    fun schedule(delayMs: Long, boundaryToken: Long) {
        scope.launch(dispatcher) {
            mutex.withLock {
                activeJob?.cancel()
                currentBoundaryToken = boundaryToken
                if (delayMs <= 0L) {
                    onBoundaryReached(boundaryToken)
                } else {
                    activeJob = launch {
                        delay(delayMs)
                        mutex.withLock {
                            if (currentBoundaryToken == boundaryToken) {
                                onBoundaryReached(boundaryToken)
                            }
                        }
                    }
                }
            }
        }
    }

    fun cancel() {
        scope.launch(dispatcher) {
            mutex.withLock {
                activeJob?.cancel()
                activeJob = null
                currentBoundaryToken = null
            }
        }
    }

    suspend fun cancelSuspending() {
        mutex.withLock {
            activeJob?.cancel()
            activeJob = null
            currentBoundaryToken = null
        }
    }

    val hasActiveJob: Boolean
        get() = activeJob?.isActive == true
}
