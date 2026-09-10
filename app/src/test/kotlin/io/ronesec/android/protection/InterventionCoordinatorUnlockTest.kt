package io.ronesec.android.protection

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.ronesec.android.data.PolicyStore
import io.ronesec.android.data.WattimDatabase
import io.ronesec.android.platform.accessibility.EventIngress
import io.ronesec.android.platform.accessibility.SubscriptionController
import io.ronesec.android.platform.time.TemporalBoundaryScheduler
import io.ronesec.domain.model.FakeMonotonicClock
import io.ronesec.domain.model.FakeWallClock
import io.ronesec.domain.model.SessionId
import io.ronesec.domain.model.TargetConfig
import io.ronesec.domain.policy.EffectiveInterventionConfig
import io.ronesec.domain.protection.ProtectionEvent
import io.ronesec.domain.protection.ProtectionState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class InterventionCoordinatorUnlockTest {

    @Test
    fun unlockResyncUsesLiveGenerationAndRejectsStaleUnlock() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val database = WattimDatabase.createInMemory(
            ApplicationProvider.getApplicationContext<Context>()
        )
        try {
            val wallClock = FakeWallClock(Instant.parse("2026-09-10T12:00:00Z"))
            val policyStore = PolicyStore(database, wallClock, backgroundScope, dispatcher)
            policyStore.awaitReady()
            val ingress = EventIngress()
            val resyncGenerations = mutableListOf<Long>()
            val storeWriter = ProtectionStoreWriter(policyStore, backgroundScope, dispatcher)
            val scheduler = TemporalBoundaryScheduler(backgroundScope, dispatcher) {}
            val executor = EffectExecutor(
                overlayPort = NoOpOverlayPort(),
                audioPort = NoOpAudioPort(),
                homePort = object : HomePort {
                    override fun sendToHome(): Boolean = true
                },
                resyncPort = object : ForegroundResyncPort {
                    override fun requestResync(generation: Long, requestSequence: Long) {
                        resyncGenerations += generation
                    }
                },
                protectionStatusPort = ProtectionStatusPort {},
                storeWriter = storeWriter,
                scheduler = scheduler,
                subscriptionController = SubscriptionController()
            )
            val coordinator = InterventionCoordinator(
                policyStore = policyStore,
                eventIngress = ingress,
                effectExecutor = executor,
                journal = ProtectionEventJournal(),
                wallClock = wallClock,
                monotonicClock = FakeMonotonicClock(1_000L),
                scope = this,
                dispatcher = dispatcher,
                processNonce = 1L
            )

            coordinator.onServiceConnected(7L)
            coordinator.start()
            advanceUntilIdle()
            coordinator.onScreenOff()
            advanceUntilIdle()
            assertTrue(
                "Expected Suspended, was ${coordinator.protectionState.value}",
                coordinator.protectionState.value is ProtectionState.Suspended
            )

            coordinator.onScreenUnlocked()
            advanceUntilIdle()
            assertEquals(listOf(7L), resyncGenerations)
            assertTrue(coordinator.protectionState.value is ProtectionState.Idle)

            coordinator.onScreenOff()
            advanceUntilIdle()
            ingress.sendControlEvent(ProtectionEvent.ScreenUnlocked(generation = 6L))
            advanceUntilIdle()

            assertEquals(listOf(7L), resyncGenerations)
            assertTrue(coordinator.protectionState.value is ProtectionState.Suspended)
            coordinator.stop()
        } finally {
            database.close()
        }
    }

    @Test
    fun screenOff_whileGranted_causesNewInterventionUponUnlockResync() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val database = WattimDatabase.createInMemory(
            ApplicationProvider.getApplicationContext<Context>()
        )
        try {
            val wallClock = FakeWallClock(Instant.parse("2026-09-10T12:00:00Z"))
            val monotonicClock = FakeMonotonicClock(1_000L)
            val policyStore = PolicyStore(database, wallClock, backgroundScope, dispatcher)
            policyStore.awaitReady()

            val targetPackage = "org.telegram.messenger"
            policyStore.saveTarget(
                TargetConfig(
                    packageName = targetPackage,
                    displayName = "Telegram",
                    durationMs = 8_000L,
                    reinterventionMs = 300_000L
                )
            ).getOrThrow()
            advanceUntilIdle()

            val ingress = EventIngress()
            var showedInterventionCount = 0
            var lastResyncGeneration: Long? = null

            val overlayPort = object : OverlayPort {
                override fun showIntervention(sessionId: SessionId, cycle: Int, config: EffectiveInterventionConfig) {
                    showedInterventionCount++
                }
                override fun showBlock(sessionId: SessionId, cycle: Int, packageName: String, until: Instant?) {}
                override fun updateOverlayComplete(sessionId: SessionId, cycle: Int) {}
                override fun dismissOverlay(sessionId: SessionId?) {}
            }

            lateinit var coordinator: InterventionCoordinator
            val executor = EffectExecutor(
                overlayPort = overlayPort,
                audioPort = NoOpAudioPort(),
                homePort = object : HomePort {
                    override fun sendToHome(): Boolean = true
                },
                resyncPort = object : ForegroundResyncPort {
                    override fun requestResync(generation: Long, requestSequence: Long) {
                        lastResyncGeneration = generation
                        // Simulate service resync falling back to lastPackage (Telegram)
                        coordinator.onForegroundCandidate(
                            ProtectionEvent.ForegroundCandidate(
                                packageName = targetPackage,
                                sourceUptimeMs = monotonicClock.elapsedRealtimeMs(),
                                eventSequence = requestSequence
                            )
                        )
                    }
                },
                protectionStatusPort = ProtectionStatusPort {},
                storeWriter = ProtectionStoreWriter(policyStore, backgroundScope, dispatcher),
                scheduler = TemporalBoundaryScheduler(backgroundScope, dispatcher) {},
                subscriptionController = SubscriptionController()
            )

            coordinator = InterventionCoordinator(
                policyStore = policyStore,
                eventIngress = ingress,
                effectExecutor = executor,
                journal = ProtectionEventJournal(),
                wallClock = wallClock,
                monotonicClock = monotonicClock,
                scope = this,
                dispatcher = dispatcher,
                processNonce = 1L
            )

            coordinator.onServiceConnected(1L)
            coordinator.start()
            advanceUntilIdle()

            // 1. User enters Telegram -> triggers 1st intervention
            coordinator.onForegroundCandidate(
                ProtectionEvent.ForegroundCandidate(targetPackage, monotonicClock.elapsedRealtimeMs(), 1L)
            )
            advanceUntilIdle()
            val state1 = coordinator.protectionState.value
            assertTrue("Expected Intervening, was $state1", state1 is ProtectionState.Intervening)
            assertEquals(1, showedInterventionCount)

            // Simulate breathing completion and user clicking Continue
            val session1 = (state1 as ProtectionState.Intervening).session
            coordinator.onOverlayAttached(session1.sessionId, session1.cycle)
            advanceUntilIdle()
            monotonicClock.advanceMillis(8_000L)
            coordinator.onBreathingDeadlineReached(session1.sessionId, session1.cycle)
            advanceUntilIdle()
            coordinator.onActionContinue(session1.sessionId, session1.cycle)
            advanceUntilIdle()

            // State is now Granted with ACTIVE_SESSION_PERMIT
            assertTrue("Expected Granted, was ${coordinator.protectionState.value}", coordinator.protectionState.value is ProtectionState.Granted)

            // 2. User turns off screen
            coordinator.onScreenOff()
            advanceUntilIdle()
            assertTrue("Expected Suspended, was ${coordinator.protectionState.value}", coordinator.protectionState.value is ProtectionState.Suspended)

            // 3. User turns on screen and unlocks
            coordinator.onScreenUnlocked()
            advanceUntilIdle()

            // 4. Verify resync triggered and produced a NEW intervention!
            assertEquals(1L, lastResyncGeneration)
            val state2 = coordinator.protectionState.value
            assertTrue("Expected new Intervening state, was $state2", state2 is ProtectionState.Intervening)
            assertEquals("ShowIntervention should have been called twice (initial + after unlock)", 2, showedInterventionCount)

            coordinator.stop()
        } finally {
            database.close()
        }
    }
}
