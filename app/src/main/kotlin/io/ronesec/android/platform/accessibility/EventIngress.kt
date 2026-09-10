package io.ronesec.android.platform.accessibility

import io.ronesec.domain.protection.ProtectionEvent
import kotlinx.coroutines.channels.Channel
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Bounded event ingress with single serialized consumer support.
 * - Low-volume control events (Exit, Continue, emergency, lifecycle, boundary, policy) are lossless and never dropped.
 * - Foreground evidence is coalescible (deduplicating identical consecutive package candidates without losing A -> B -> A transitions).
 * - Bounded foreground buffer (default 16): on overflow, marks dirty and enqueues ResyncRequested without unbounded memory growth.
 * - No coroutine launched per accessibility callback.
 * Satisfies F24, Section 3.1, Section 11.2 (T09).
 */
class EventIngress(
    val foregroundCapacity: Int = 16
) {
    // Lossless queue for control events
    private val controlQueue = ConcurrentLinkedQueue<ProtectionEvent>()

    // Bounded queue for foreground candidate evidence
    private val foregroundQueue = ArrayDeque<ProtectionEvent.ForegroundCandidate>()
    private val foregroundLock = Any()

    // Conflated wakeup signal for consumer
    private val signalChannel = Channel<Unit>(Channel.CONFLATED)

    private val isDirty = AtomicBoolean(false)
    private val highWaterMark = AtomicInteger(0)
    private val overflowResyncCount = AtomicInteger(0)

    private var currentGeneration: Long = 0L
    private var resyncSequence: Long = 0L

    fun setGeneration(generation: Long) {
        currentGeneration = generation
    }

    /**
     * Lossless enqueue for low-volume control events. Never dropped.
     */
    fun sendControlEvent(event: ProtectionEvent) {
        controlQueue.add(event)
        signalChannel.trySend(Unit)
    }

    /**
     * Non-blocking enqueue for foreground evidence.
     * No coroutines launched. Copies primitives, coalesces consecutive identical packages, bounded capacity.
     */
    fun sendForegroundCandidate(candidate: ProtectionEvent.ForegroundCandidate): Boolean {
        synchronized(foregroundLock) {
            if (foregroundQueue.isNotEmpty()) {
                val last = foregroundQueue.last
                // Coalesce if identical package to avoid queue bloat without losing A -> B -> A transitions
                if (last.packageName == candidate.packageName) {
                    foregroundQueue.removeLast()
                    foregroundQueue.addLast(candidate)
                    signalChannel.trySend(Unit)
                    return true
                }
            }

            if (foregroundQueue.size < foregroundCapacity) {
                foregroundQueue.addLast(candidate)
                val currentSize = foregroundQueue.size
                highWaterMark.updateAndGet { current -> maxOf(current, currentSize) }
                signalChannel.trySend(Unit)
                return true
            } else {
                // Capacity exhausted: mark dirty and trigger ResyncRequested
                isDirty.set(true)
                overflowResyncCount.incrementAndGet()
                signalChannel.trySend(Unit)
                return false
            }
        }
    }

    /**
     * Poll next event according to priority:
     * 1. Lossless control events
     * 2. Dirty resync request (from ingress overflow)
     * 3. Foreground candidates
     */
    fun pollNextEvent(): ProtectionEvent? {
        // Priority 1: Lossless control events
        val control = controlQueue.poll()
        if (control != null) {
            return control
        }

        // Priority 2: Dirty resync request from overload
        if (isDirty.compareAndSet(true, false)) {
            return ProtectionEvent.ResyncRequested(
                generation = currentGeneration,
                requestSequence = ++resyncSequence
            )
        }

        // Priority 3: Foreground evidence
        synchronized(foregroundLock) {
            if (foregroundQueue.isNotEmpty()) {
                return foregroundQueue.removeFirst()
            }
        }

        return null
    }

    /**
     * Suspends until the next event is available.
     */
    suspend fun receiveNextEvent(): ProtectionEvent {
        while (true) {
            val event = pollNextEvent()
            if (event != null) {
                return event
            }
            signalChannel.receive()
        }
    }

    fun clear() {
        controlQueue.clear()
        synchronized(foregroundLock) {
            foregroundQueue.clear()
        }
        isDirty.set(false)
    }

    val currentHighWaterMark: Int
        get() = highWaterMark.get()

    val currentOverflowResyncCount: Int
        get() = overflowResyncCount.get()

    val isForegroundEmpty: Boolean
        get() = synchronized(foregroundLock) { foregroundQueue.isEmpty() }

    val isControlEmpty: Boolean
        get() = controlQueue.isEmpty()
}
