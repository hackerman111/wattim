package io.ronesec.android.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.ronesec.android.data.entity.AppSettingsEntity
import io.ronesec.android.data.entity.OpenAttemptEntity
import io.ronesec.domain.model.FakeWallClock
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
class T20_StatisticsStoreTest {

    private lateinit var database: WattimDatabase
    private lateinit var wallClock: FakeWallClock
    private val testDispatcher = StandardTestDispatcher()

    private val zoneId = ZoneId.of("UTC")

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = WattimDatabase.createInMemory(context)
        // 2026-09-09 12:00:00 UTC
        wallClock = FakeWallClock(Instant.parse("2026-09-09T12:00:00Z"), zoneId)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun todayAndAllTimeStatisticsExcludesInterruptedAndRespectsDayBounds() = runTest(testDispatcher) {
        val statsStore = StatisticsStore(database, wallClock, testDispatcher)

        // Initialize default settings (savedSessionMinutes = 7)
        database.appSettingsDao().setSettings(AppSettingsEntity(id = 1, savedSessionMinutes = 7))

        val nowMs = wallClock.now().toEpochMilli()
        val yesterdayMs = nowMs - 24 * 3600 * 1000L

        // 1. Yesterday attempts (must be excluded from today, but included in all-time)
        database.openAttemptDao().insertIdempotent(
            OpenAttemptEntity(
                attemptId = "yest-1",
                sessionId = "s-y1",
                cycle = 1,
                packageName = "com.app.a",
                displayNameAtAttempt = "App A",
                generation = 1L,
                kind = "ENTRY",
                timestamp = yesterdayMs,
                outcome = "ABANDONED",
                resolvedAt = yesterdayMs
            )
        )
        database.openAttemptDao().insertIdempotent(
            OpenAttemptEntity(
                attemptId = "yest-2",
                sessionId = "s-y2",
                cycle = 1,
                packageName = "com.app.a",
                displayNameAtAttempt = "App A",
                generation = 1L,
                kind = "ENTRY",
                timestamp = yesterdayMs,
                outcome = "CONTINUED",
                resolvedAt = yesterdayMs
            )
        )

        // 2. Today attempts:
        // App A: 2 CONTINUED, 2 ABANDONED
        // App B: 1 BLOCKED, 1 ABANDONED
        // 1 INTERRUPTED (process death/crash -> must be excluded from totals!)
        database.openAttemptDao().insertIdempotent(
            OpenAttemptEntity(
                attemptId = "today-1",
                sessionId = "s-t1",
                cycle = 1,
                packageName = "com.app.a",
                displayNameAtAttempt = "App A",
                generation = 1L,
                kind = "ENTRY",
                timestamp = nowMs - 3600_000L,
                outcome = "CONTINUED",
                resolvedAt = nowMs - 3590_000L
            )
        )
        database.openAttemptDao().insertIdempotent(
            OpenAttemptEntity(
                attemptId = "today-2",
                sessionId = "s-t2",
                cycle = 1,
                packageName = "com.app.a",
                displayNameAtAttempt = "App A",
                generation = 1L,
                kind = "ENTRY",
                timestamp = nowMs - 3000_000L,
                outcome = "CONTINUED",
                resolvedAt = nowMs - 2990_000L
            )
        )
        database.openAttemptDao().insertIdempotent(
            OpenAttemptEntity(
                attemptId = "today-3",
                sessionId = "s-t3",
                cycle = 1,
                packageName = "com.app.a",
                displayNameAtAttempt = "App A",
                generation = 1L,
                kind = "ENTRY",
                timestamp = nowMs - 2000_000L,
                outcome = "ABANDONED",
                resolvedAt = nowMs - 1990_000L
            )
        )
        database.openAttemptDao().insertIdempotent(
            OpenAttemptEntity(
                attemptId = "today-4",
                sessionId = "s-t4",
                cycle = 1,
                packageName = "com.app.a",
                displayNameAtAttempt = "App A",
                generation = 1L,
                kind = "ENTRY",
                timestamp = nowMs - 1000_000L,
                outcome = "ABANDONED",
                resolvedAt = nowMs - 990_000L
            )
        )
        database.openAttemptDao().insertIdempotent(
            OpenAttemptEntity(
                attemptId = "today-5",
                sessionId = "s-t5",
                cycle = 1,
                packageName = "com.app.b",
                displayNameAtAttempt = "App B",
                generation = 1L,
                kind = "ENTRY",
                timestamp = nowMs - 500_000L,
                outcome = "BLOCKED",
                resolvedAt = nowMs - 490_000L
            )
        )
        database.openAttemptDao().insertIdempotent(
            OpenAttemptEntity(
                attemptId = "today-6",
                sessionId = "s-t6",
                cycle = 1,
                packageName = "com.app.b",
                displayNameAtAttempt = "App B",
                generation = 1L,
                kind = "ENTRY",
                timestamp = nowMs - 200_000L,
                outcome = "ABANDONED",
                resolvedAt = nowMs - 190_000L
            )
        )
        database.openAttemptDao().insertIdempotent(
            OpenAttemptEntity(
                attemptId = "today-interrupted",
                sessionId = "s-tint",
                cycle = 1,
                packageName = "com.app.b",
                displayNameAtAttempt = "App B",
                generation = 1L,
                kind = "ENTRY",
                timestamp = nowMs - 100_000L,
                outcome = "INTERRUPTED",
                resolvedAt = nowMs - 90_000L
            )
        )

        // Verify Today summary:
        // Total product visible: 2 CONTINUED + 3 ABANDONED + 1 BLOCKED = 6
        // Continued: 2
        // Closed: 4 (3 ABANDONED + 1 BLOCKED)
        // Avoided: 4 * 100 / 6 = 67%
        val today = statsStore.getTodaySummary()
        assertEquals(6, today.totalAttempts)
        assertEquals(2, today.continuedCount)
        assertEquals(4, today.closedCount)
        assertEquals(67, today.avoidedPercent)

        // Verify All-time saved life:
        // Total closed: 1 (yesterday) + 4 (today) = 5
        // Multiplier: 7 minutes
        // Total saved minutes: 5 * 7 = 35 minutes
        val allTime = statsStore.getAllTimeSavedLife()
        assertEquals(5, allTime.totalClosedCount)
        assertEquals(7, allTime.multiplierMinutes)
        assertEquals(35L, allTime.totalSavedMinutes)
        assertEquals("35 мин", allTime.formattedSavedTime)

        // Change saved-session multiplier from 7 to 15
        database.appSettingsDao().updateSavedSessionMinutes(15)
        val allTimeUpdated = statsStore.getAllTimeSavedLife()
        assertEquals(5, allTimeUpdated.totalClosedCount)
        assertEquals(15, allTimeUpdated.multiplierMinutes)
        assertEquals(75L, allTimeUpdated.totalSavedMinutes) // 5 * 15 = 75 мин = 1 ч 15 мин
        assertEquals("1 ч 15 мин", allTimeUpdated.formattedSavedTime)

        // Verify Per-App Stats:
        // App A: 4 openings, 2 closed
        // App B: 2 openings, 2 closed
        // Sorted by totalOpenings DESC: App A first, then App B
        val perApp = statsStore.getPerAppStatsToday()
        assertEquals(2, perApp.size)
        assertEquals("com.app.a", perApp[0].packageName)
        assertEquals(4, perApp[0].totalOpenings)
        assertEquals(2, perApp[0].totalClosed)

        assertEquals("com.app.b", perApp[1].packageName)
        assertEquals(2, perApp[1].totalOpenings)
        assertEquals(2, perApp[1].totalClosed)
    }
}
