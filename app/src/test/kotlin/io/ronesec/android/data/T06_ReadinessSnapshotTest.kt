package io.ronesec.android.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.ronesec.domain.model.CompiledSchedule
import io.ronesec.domain.model.FakeWallClock
import io.ronesec.domain.model.GlobalPause
import io.ronesec.domain.model.ScheduleOverride
import io.ronesec.domain.model.ScheduleType
import io.ronesec.domain.model.TargetConfig
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import io.ronesec.domain.model.AttemptKind
import io.ronesec.domain.model.AttemptOutcome
import io.ronesec.domain.model.AttemptRecord
import io.ronesec.domain.model.GrantOrigin
import io.ronesec.domain.model.SessionId
import io.ronesec.domain.model.TimedGrant
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class T06_ReadinessSnapshotTest {

    private lateinit var database: WattimDatabase
    private lateinit var wallClock: FakeWallClock
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = WattimDatabase.createInMemory(context)
        wallClock = FakeWallClock(Instant.parse("2026-09-09T12:00:00Z"))
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun storeReadinessAndCoherentSnapshotPublication() = runTest {
        val store = PolicyStore(
            database = database,
            wallClock = wallClock,
            scope = backgroundScope,
            ioDispatcher = StandardTestDispatcher(testScheduler)
        )

        // Await ready ensures loading finishes
        val initialSnapshot = store.awaitReady()
        assertFalse(store.isLoading.value)
        assertNotNull(initialSnapshot)
        assertEquals(1L, initialSnapshot.revision)
        assertTrue(initialSnapshot.targets.isEmpty())
        assertEquals(GlobalPause.None, initialSnapshot.globalPause)

        // Add a target
        val target = TargetConfig(
            packageName = "com.test.app",
            displayName = "Test App",
            durationMs = 8_000L
        )
        val saveResult = store.saveTarget(target)
        assertTrue(saveResult.isSuccess)

        val updatedSnapshot = store.currentSnapshot
        assertEquals("Revision must increment monotonically", 2L, updatedSnapshot.revision)
        assertEquals(1, updatedSnapshot.targets.size)
        assertEquals("Test App", updatedSnapshot.targets["com.test.app"]?.displayName)

        // Add a schedule with members and overrides atomically
        val schedule = CompiledSchedule(
            id = 10L,
            name = "Deep Work",
            weekdayMask = 0x7F,
            startMinute = 10 * 60,
            endMinute = 12 * 60,
            enabled = true,
            type = ScheduleType.INTERVENTION,
            targetPackages = setOf("com.test.app"),
            overrides = mapOf(
                "com.test.app" to ScheduleOverride(durationMs = 15_000L, reinterventionMs = 60_000L)
            )
        )
        val scheduleResult = store.saveSchedule(schedule)
        assertTrue(scheduleResult.isSuccess)

        val scheduleSnapshot = store.currentSnapshot
        assertEquals(3L, scheduleSnapshot.revision)
        assertEquals(1, scheduleSnapshot.activeSchedules.size)
        val compiled = scheduleSnapshot.activeSchedules.first()
        assertEquals("Deep Work", compiled.name)
        assertTrue(compiled.targetPackages.contains("com.test.app"))
        assertEquals(15_000L, compiled.overrides["com.test.app"]?.durationMs)
    }

    @Test
    fun restartReconstructsIdenticalPolicyAndFinalizesUnresolvedAttemptsWithExplicitLoading() = runTest {
        val store1 = PolicyStore(
            database = database,
            wallClock = wallClock,
            scope = backgroundScope,
            ioDispatcher = StandardTestDispatcher(testScheduler)
        )
        store1.awaitReady()

        // Configure target
        val target = TargetConfig(packageName = "com.test.restart", displayName = "Restart Target", durationMs = 10_000L)
        store1.saveTarget(target)

        // Configure grant
        val grant = TimedGrant(
            packageName = "com.test.restart",
            grantId = "g-restart-1",
            origin = GrantOrigin.REINTERVENTION,
            createdAt = wallClock.now(),
            expiresAt = wallClock.now().plusSeconds(300)
        )
        store1.grantAccess(grant)

        // Record a resolved attempt
        val resolvedRecord = AttemptRecord(
            attemptId = "att-res-1",
            sessionId = SessionId(1, 1, 1),
            cycle = 1,
            packageName = "com.test.restart",
            displayNameAtAttempt = "Restart Target",
            generation = 1L,
            kind = AttemptKind.ENTRY,
            timestamp = wallClock.now(),
            outcome = AttemptOutcome.CONTINUED,
            resolvedAt = wallClock.now()
        )
        store1.recordAttempt(resolvedRecord)

        // Record an UNRESOLVED attempt (simulating process death while user was breathing/intervening)
        val unresolvedRecord = AttemptRecord(
            attemptId = "att-unres-1",
            sessionId = SessionId(1, 1, 2),
            cycle = 1,
            packageName = "com.test.restart",
            displayNameAtAttempt = "Restart Target",
            generation = 1L,
            kind = AttemptKind.ENTRY,
            timestamp = wallClock.now(),
            outcome = null,
            resolvedAt = null
        )
        store1.recordAttempt(unresolvedRecord)

        // Simulate process restart with a fresh store against same DB
        val store2 = PolicyStore(
            database = database,
            wallClock = wallClock,
            scope = backgroundScope,
            ioDispatcher = StandardTestDispatcher(testScheduler)
        )

        // 1. Initially Loading is true before awaitReady
        assertTrue("Store must be in explicit Loading state upon creation", store2.isLoading.value)

        // 2. Await ready publishes coherent snapshot
        val restoredSnapshot = store2.awaitReady()
        assertFalse("Store must not be loading after awaitReady", store2.isLoading.value)

        // 3. Snapshot state matches store1
        assertEquals(store1.currentSnapshot.revision, restoredSnapshot.revision)
        assertNotNull(restoredSnapshot.targets["com.test.restart"])
        assertEquals("Restart Target", restoredSnapshot.targets["com.test.restart"]?.displayName)
        assertNotNull(restoredSnapshot.activeGrants["com.test.restart"])
        assertEquals("g-restart-1", restoredSnapshot.activeGrants["com.test.restart"]?.grantId)

        // 4. Unresolved attempt from dead generation finalized as INTERRUPTED on startup
        val deadAttempt = database.openAttemptDao().getAttempt("att-unres-1")
        assertNotNull(deadAttempt)
        assertEquals("INTERRUPTED", deadAttempt!!.outcome)
        assertEquals(wallClock.now().toEpochMilli(), deadAttempt.resolvedAt)

        // 5. Already resolved attempt was untouched
        val resolvedAttempt = database.openAttemptDao().getAttempt("att-res-1")
        assertNotNull(resolvedAttempt)
        assertEquals("CONTINUED", resolvedAttempt!!.outcome)
    }
}
