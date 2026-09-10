package io.ronesec.android.ui.stats

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.ronesec.android.data.PolicyStore
import io.ronesec.android.data.StatisticsStore
import io.ronesec.android.data.WattimDatabase
import io.ronesec.android.data.entity.OpenAttemptEntity
import io.ronesec.domain.model.FakeWallClock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class StatsViewModelTest {

    private lateinit var database: WattimDatabase
    private lateinit var wallClock: FakeWallClock

    private val zoneId = ZoneId.of("UTC")

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = WattimDatabase.createInMemory(context)
        wallClock = FakeWallClock(Instant.parse("2026-09-09T12:00:00Z"), zoneId)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `empty database produces truthful zero state`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val policyStore = PolicyStore(database, wallClock, backgroundScope, dispatcher)
        val statsStore = StatisticsStore(database, wallClock, dispatcher)
        policyStore.awaitReady()
        advanceUntilIdle()

        val viewModel = StatsViewModel(
            statisticsStore = statsStore,
            policyStore = policyStore,
            wallClock = wallClock,
            coroutineScope = backgroundScope,
            dispatcher = dispatcher
        )
        advanceUntilIdle()

        val state = viewModel.uiState.first { !it.isLoading }
        assertFalse(state.isLoading)
        assertEquals("0 min", state.allTimeSavedDuration)
        assertEquals(0L, state.allTimeSavedMinutes)
        assertEquals(0, state.allTimeAvoidedCount)
        assertEquals("0 min", state.savedTodayDuration)
        assertEquals(0L, state.savedTodayMinutes)
        assertEquals(7, state.multiplierMinutes)
        assertEquals(0, state.todayTotalAttempts)
        assertEquals(0, state.todayContinuedCount)
        assertEquals(0, state.todayClosedCount)
        assertEquals(0, state.todayAvoidedPercent)
        assertTrue(state.appStatsToday.isEmpty())
    }

    @Test
    fun `stats reflect attempts and update when multiplier and language change`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val policyStore = PolicyStore(database, wallClock, backgroundScope, dispatcher)
        val statsStore = StatisticsStore(database, wallClock, dispatcher)
        policyStore.awaitReady()
        advanceUntilIdle()

        val nowMs = wallClock.now().toEpochMilli()

        // 1 yesterday closed attempt
        database.openAttemptDao().insertIdempotent(
            OpenAttemptEntity(
                attemptId = "y-1",
                sessionId = "s-y1",
                cycle = 1,
                packageName = "com.app.yt",
                displayNameAtAttempt = "YouTube",
                generation = 1L,
                kind = "ENTRY",
                timestamp = nowMs - 24 * 3600 * 1000L,
                outcome = "ABANDONED",
                resolvedAt = nowMs - 24 * 3600 * 1000L
            )
        )

        // 2 today attempts for YouTube: 1 CONTINUED, 1 ABANDONED
        database.openAttemptDao().insertIdempotent(
            OpenAttemptEntity(
                attemptId = "t-1",
                sessionId = "s-t1",
                cycle = 1,
                packageName = "com.app.yt",
                displayNameAtAttempt = "YouTube",
                generation = 1L,
                kind = "ENTRY",
                timestamp = nowMs - 1000_000L,
                outcome = "CONTINUED",
                resolvedAt = nowMs - 990_000L
            )
        )
        database.openAttemptDao().insertIdempotent(
            OpenAttemptEntity(
                attemptId = "t-2",
                sessionId = "s-t2",
                cycle = 1,
                packageName = "com.app.yt",
                displayNameAtAttempt = "YouTube",
                generation = 1L,
                kind = "ENTRY",
                timestamp = nowMs - 500_000L,
                outcome = "ABANDONED",
                resolvedAt = nowMs - 490_000L
            )
        )

        // 3 today attempts for Reddit: 3 BLOCKED
        for (i in 1..3) {
            database.openAttemptDao().insertIdempotent(
                OpenAttemptEntity(
                    attemptId = "t-reddit-$i",
                    sessionId = "s-r-$i",
                    cycle = 1,
                    packageName = "com.app.reddit",
                    displayNameAtAttempt = "Reddit",
                    generation = 1L,
                    kind = "ENTRY",
                    timestamp = nowMs - 200_000L + i * 1000L,
                    outcome = "BLOCKED",
                    resolvedAt = nowMs - 190_000L + i * 1000L
                )
            )
        }

        val viewModel = StatsViewModel(
            statisticsStore = statsStore,
            policyStore = policyStore,
            wallClock = wallClock,
            coroutineScope = backgroundScope,
            dispatcher = dispatcher
        )
        advanceUntilIdle()

        var state = viewModel.uiState.first { !it.isLoading }
        // Total closed: 1 (yesterday) + 1 (today yt) + 3 (today reddit) = 5
        assertEquals(5, state.allTimeAvoidedCount)
        assertEquals(7, state.multiplierMinutes)
        assertEquals(35L, state.allTimeSavedMinutes) // 5 * 7
        assertEquals("35 min", state.allTimeSavedDuration)

        // Today closed: 1 + 3 = 4. Today total: 2 (yt) + 3 (reddit) = 5
        assertEquals(4, state.todayClosedCount)
        assertEquals(1, state.todayContinuedCount)
        assertEquals(5, state.todayTotalAttempts)
        assertEquals(80, state.todayAvoidedPercent) // 4 * 100 / 5 = 80%
        assertEquals(28L, state.savedTodayMinutes) // 4 * 7 = 28 min
        assertEquals("28 min", state.savedTodayDuration)

        // Per app stats sorted by totalOpenings DESC:
        // Reddit: 3 openings, 3 closed
        // YouTube: 2 openings, 1 closed
        assertEquals(2, state.appStatsToday.size)
        assertEquals("com.app.reddit", state.appStatsToday[0].packageName)
        assertEquals(3, state.appStatsToday[0].totalOpenings)
        assertEquals(3, state.appStatsToday[0].totalClosed)

        assertEquals("com.app.yt", state.appStatsToday[1].packageName)
        assertEquals(2, state.appStatsToday[1].totalOpenings)
        assertEquals(1, state.appStatsToday[1].totalClosed)

        // Update multiplier to 10 min in settings
        policyStore.setSavedSessionMinutes(10)
        advanceUntilIdle()

        state = viewModel.uiState.first { it.multiplierMinutes == 10 }
        assertEquals(10, state.multiplierMinutes)
        assertEquals(50L, state.allTimeSavedMinutes) // 5 * 10 = 50 min
        assertEquals("50 min", state.allTimeSavedDuration)
        assertEquals(40L, state.savedTodayMinutes) // 4 * 10 = 40 min
        assertEquals("40 min", state.savedTodayDuration)

        // Update language to Russian
        policyStore.setLanguage("РУССКИЙ")
        advanceUntilIdle()

        val ruState = viewModel.uiState.first { it.allTimeSavedDuration.endsWith("мин") }
        assertEquals("50 мин", ruState.allTimeSavedDuration)
        assertEquals("40 мин", ruState.savedTodayDuration)
    }
}
