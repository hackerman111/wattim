package io.ronesec.android.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.ronesec.android.data.entity.OpenAttemptEntity
import io.ronesec.android.data.entity.TargetAppEntity
import io.ronesec.domain.model.BackoffConfig
import io.ronesec.domain.model.FakeWallClock
import io.ronesec.domain.policy.Backoff
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class T03_BackoffHistoryRoomTest {

    private lateinit var database: WattimDatabase
    private lateinit var wallClock: FakeWallClock
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private val targetPackage = "com.example.target"

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
    fun rollingWindowFetchesOnlyGenuineEntryAttempts() = runTest(testDispatcher) {
        val nowMs = wallClock.now().toEpochMilli()
        val window60m = 60 * 60 * 1000L // 3,600,000 ms

        // Insert target app
        database.targetAppDao().insertOrUpdate(
            TargetAppEntity(
                packageName = targetPackage,
                displayName = "Target App",
                growthEnabled = true,
                growthPercent = 20,
                growthWindowMs = window60m
            )
        )

        // 1. Genuine ENTRY inside window (30m ago)
        database.openAttemptDao().insertIdempotent(
            OpenAttemptEntity(
                attemptId = "att-1",
                sessionId = "s-1",
                cycle = 1,
                packageName = targetPackage,
                displayNameAtAttempt = "Target App",
                generation = 1L,
                kind = "ENTRY",
                timestamp = nowMs - 30 * 60 * 1000L,
                outcome = "CONTINUED",
                resolvedAt = nowMs - 29 * 60 * 1000L
            )
        )

        // 2. Genuine ENTRY inside window (10m ago)
        database.openAttemptDao().insertIdempotent(
            OpenAttemptEntity(
                attemptId = "att-2",
                sessionId = "s-2",
                cycle = 1,
                packageName = targetPackage,
                displayNameAtAttempt = "Target App",
                generation = 1L,
                kind = "ENTRY",
                timestamp = nowMs - 10 * 60 * 1000L,
                outcome = "ABANDONED",
                resolvedAt = nowMs - 9 * 60 * 1000L
            )
        )

        // 3. REINTERVENTION attempt (not ENTRY -> must be excluded from backoff count!)
        database.openAttemptDao().insertIdempotent(
            OpenAttemptEntity(
                attemptId = "att-3",
                sessionId = "s-2",
                cycle = 2,
                packageName = targetPackage,
                displayNameAtAttempt = "Target App",
                generation = 1L,
                kind = "REINTERVENTION",
                timestamp = nowMs - 5 * 60 * 1000L,
                outcome = "CONTINUED",
                resolvedAt = nowMs - 4 * 60 * 1000L
            )
        )

        // 4. Old ENTRY outside window (90m ago)
        database.openAttemptDao().insertIdempotent(
            OpenAttemptEntity(
                attemptId = "att-4",
                sessionId = "s-0",
                cycle = 1,
                packageName = targetPackage,
                displayNameAtAttempt = "Target App",
                generation = 1L,
                kind = "ENTRY",
                timestamp = nowMs - 90 * 60 * 1000L,
                outcome = "CONTINUED",
                resolvedAt = nowMs - 89 * 60 * 1000L
            )
        )

        // 5. Another package ENTRY inside window
        database.openAttemptDao().insertIdempotent(
            OpenAttemptEntity(
                attemptId = "att-5",
                sessionId = "s-other",
                cycle = 1,
                packageName = "com.other.app",
                displayNameAtAttempt = "Other",
                generation = 1L,
                kind = "ENTRY",
                timestamp = nowMs - 15 * 60 * 1000L,
                outcome = "CONTINUED",
                resolvedAt = nowMs - 14 * 60 * 1000L
            )
        )

        // Query recent ENTRY attempts for targetPackage since (now - window)
        val sinceMs = nowMs - window60m
        val recentTargetEntries = database.openAttemptDao().getRecentEntryAttempts(targetPackage, sinceMs)

        // Only CONTINUED entry (att-1) is counted; ABANDONED entry (att-2) must be excluded!
        assertEquals(1, recentTargetEntries.size)
        assertTrue(recentTargetEntries.any { it.attemptId == "att-1" })

        // Check backoff delay calculation using this history
        val priorCount = Backoff.countEligiblePriorEntries(
            entryTimestamps = recentTargetEntries.map { it.timestamp },
            nowEpochMs = nowMs,
            windowMs = window60m
        )
        assertEquals(1, priorCount)

        // N = 1: 8000 * 1.2^1 = 9600 ms (was 11520 when ABANDONED was erroneously counted)
        val delay = Backoff.resolveEffectiveDurationMs(
            baseDurationMs = 8_000L,
            config = BackoffConfig(enabled = true, percent = 20, windowMs = window60m),
            priorEntriesCount = priorCount
        )
        assertEquals(9_600L, delay)
    }
}
