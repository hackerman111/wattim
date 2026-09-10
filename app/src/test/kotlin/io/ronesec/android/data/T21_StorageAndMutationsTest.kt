package io.ronesec.android.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.ronesec.android.protection.ProtectionStoreWriter
import io.ronesec.domain.model.AttemptKind
import io.ronesec.domain.model.AttemptOutcome
import io.ronesec.domain.model.AttemptRecord
import io.ronesec.domain.model.CompiledSchedule
import io.ronesec.domain.model.FakeWallClock
import io.ronesec.domain.model.GrantOrigin
import io.ronesec.domain.model.ScheduleOverride
import io.ronesec.domain.model.ScheduleType
import io.ronesec.domain.model.SessionId
import io.ronesec.domain.model.TargetConfig
import io.ronesec.domain.model.TimedGrant
import io.ronesec.domain.protection.ProtectionEffect
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class T21_StorageAndMutationsTest {

    private lateinit var database: WattimDatabase
    private lateinit var wallClock: FakeWallClock
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private val targetPackage = "com.test.storage"

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
    fun casRowVersionPreventsLostUpdates() = runTest {
        val store = PolicyStore(database, wallClock, backgroundScope, StandardTestDispatcher(testScheduler))
        store.awaitReady()

        val initialConfig = TargetConfig(
            packageName = targetPackage,
            displayName = "Target",
            durationMs = 8_000L,
            rowVersion = 1L
        )

        // 1. Initial save succeeds
        val save1 = store.saveTarget(initialConfig)
        assertTrue(save1.isSuccess)

        val committed1 = store.currentSnapshot.targets[targetPackage]
        assertNotNull(committed1)
        assertEquals(1L, committed1!!.rowVersion) // initial version is 1L

        // 2. Stale edit with wrong rowVersion (99L) fails
        val staleEdit = initialConfig.copy(durationMs = 12_000L, rowVersion = 99L)
        val saveStale = store.saveTarget(staleEdit)
        assertTrue("Stale save must fail", saveStale.isFailure)

        // 3. Save with current version (1L) succeeds and bumps to 2L
        val validEdit = initialConfig.copy(durationMs = 12_000L, rowVersion = committed1.rowVersion)
        val saveValid = store.saveTarget(validEdit)
        assertTrue("Valid save must succeed", saveValid.isSuccess)
        val committed2 = store.currentSnapshot.targets[targetPackage]
        assertEquals(2L, committed2!!.rowVersion)
        assertEquals(12_000L, committed2.durationMs)
    }

    @Test
    fun removeTargetCascadesGrantsAndMemberships_retainsAttemptHistory() = runTest {
        val store = PolicyStore(database, wallClock, backgroundScope, StandardTestDispatcher(testScheduler))
        store.awaitReady()

        // 1. Save target
        store.saveTarget(TargetConfig(packageName = targetPackage, displayName = "Target"))

        // 2. Add a grant
        val grant = TimedGrant(
            packageName = targetPackage,
            grantId = "g-1",
            origin = GrantOrigin.EMERGENCY,
            createdAt = wallClock.now(),
            expiresAt = wallClock.now().plusSeconds(600)
        )
        store.grantAccess(grant)

        // 3. Add to a block session
        val sessionId = store.createBlockSession(
            name = "Focus",
            startTime = wallClock.now(),
            endTime = wallClock.now().plusSeconds(3600),
            targetPackages = setOf(targetPackage)
        ).getOrThrow()

        // 4. Add to a schedule with override
        val schedule = CompiledSchedule(
            id = 5L,
            name = "Night",
            weekdayMask = 0x7F,
            startMinute = 22 * 60,
            endMinute = 6 * 60,
            enabled = true,
            type = ScheduleType.HARD_BLOCK,
            targetPackages = setOf(targetPackage),
            overrides = mapOf(targetPackage to ScheduleOverride(durationMs = 10_000L))
        )
        store.saveSchedule(schedule)

        // 5. Record attempt for this target
        val attemptRecord = AttemptRecord(
            attemptId = "att-hist-1",
            sessionId = SessionId(1, 1, 1),
            cycle = 1,
            packageName = targetPackage,
            displayNameAtAttempt = "Target",
            generation = 1L,
            kind = AttemptKind.ENTRY,
            timestamp = wallClock.now(),
            outcome = AttemptOutcome.CONTINUED,
            resolvedAt = wallClock.now()
        )
        store.recordAttempt(attemptRecord)

        // Verify state before removal
        assertNotNull(store.currentSnapshot.targets[targetPackage])
        assertNotNull(store.currentSnapshot.activeGrants[targetPackage])

        // 6. Remove target
        val removeResult = store.removeTarget(targetPackage)
        assertTrue(removeResult.isSuccess)

        // Target and grant are gone
        assertNull(store.currentSnapshot.targets[targetPackage])
        assertNull(store.currentSnapshot.activeGrants[targetPackage])

        // Empty block session is deactivated
        val sessionInDb = database.blockSessionDao().getSession(sessionId)
        assertNotNull(sessionInDb)
        assertFalse("Empty session must become inactive", sessionInDb!!.active)

        // History in open_attempts is RETAINED
        val attemptInDb = database.openAttemptDao().getAttempt("att-hist-1")
        assertNotNull("open_attempts must be retained after target deletion", attemptInDb)
        assertEquals(targetPackage, attemptInDb!!.packageName)
    }

    @Test
    fun attemptInsertionIsIdempotent() = runTest {
        val store = PolicyStore(database, wallClock, backgroundScope, StandardTestDispatcher(testScheduler))
        store.awaitReady()

        val record = AttemptRecord(
            attemptId = "att-dup-1",
            sessionId = SessionId(1, 1, 1),
            cycle = 1,
            packageName = targetPackage,
            displayNameAtAttempt = "Target",
            generation = 1L,
            kind = AttemptKind.ENTRY,
            timestamp = wallClock.now()
        )

        // Insert first time
        val res1 = store.recordAttempt(record)
        assertTrue(res1.isSuccess)

        // Insert duplicate with same attemptId and (sessionId, cycle)
        val res2 = store.recordAttempt(record)
        assertTrue("Duplicate attempt insert must be idempotent and succeed", res2.isSuccess)

        // Finalize outcome
        val finResult = store.finalizeAttempt("att-dup-1", AttemptOutcome.CONTINUED, wallClock.now())
        assertTrue(finResult.isSuccess)

        val finalized = database.openAttemptDao().getAttempt("att-dup-1")
        assertNotNull(finalized)
        assertEquals("CONTINUED", finalized!!.outcome)
    }

    @Test
    fun protectionStoreWriterPersistsAttemptBeforeImmediateOutcome() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val store = PolicyStore(database, wallClock, backgroundScope, dispatcher)
        store.awaitReady()
        val writer = ProtectionStoreWriter(store, backgroundScope, dispatcher)
        val attemptId = "att-fifo-1"
        val record = AttemptRecord(
            attemptId = attemptId,
            sessionId = SessionId(1, 1, 2),
            cycle = 1,
            packageName = targetPackage,
            displayNameAtAttempt = "Target",
            generation = 1L,
            kind = AttemptKind.ENTRY,
            timestamp = wallClock.now()
        )

        writer.enqueue(ProtectionEffect.RecordAttempt(record))
        writer.enqueue(
            ProtectionEffect.CommitAttemptOutcome(
                attemptId = attemptId,
                outcome = AttemptOutcome.BLOCKED,
                resolvedAt = wallClock.now()
            )
        )
        writer.awaitIdle()

        val finalized = database.openAttemptDao().getAttempt(attemptId)
        assertNotNull(finalized)
        assertEquals("BLOCKED", finalized!!.outcome)
    }

    @Test
    fun customEmergencyMinutes_persistsAndStreamsToPresentationSettings() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val store = PolicyStore(database, wallClock, backgroundScope, dispatcher)
        store.awaitReady()

        // Initially null
        assertNull(store.presentationSettings.value.customEmergencyMinutes)

        // Set to 45 minutes
        val setResult = store.setCustomEmergencyMinutes(45)
        assertTrue(setResult.isSuccess)
        assertEquals(45, store.presentationSettings.value.customEmergencyMinutes)

        val dbSettings = database.appSettingsDao().getSettings()
        assertNotNull(dbSettings)
        assertEquals(45, dbSettings!!.customEmergencyMinutes)

        // Reset to null (remove from menu)
        val clearResult = store.setCustomEmergencyMinutes(null)
        assertTrue(clearResult.isSuccess)
        assertNull(store.presentationSettings.value.customEmergencyMinutes)

        val clearedSettings = database.appSettingsDao().getSettings()
        assertNull(clearedSettings!!.customEmergencyMinutes)
    }
}
