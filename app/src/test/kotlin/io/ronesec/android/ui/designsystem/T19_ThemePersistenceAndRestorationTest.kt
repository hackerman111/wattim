package io.ronesec.android.ui.designsystem

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.ronesec.android.data.PolicyStore
import io.ronesec.android.data.WattimDatabase
import io.ronesec.domain.model.FakeWallClock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
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

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class T19_ThemePersistenceAndRestorationTest {

    private lateinit var database: WattimDatabase
    private lateinit var wallClock: FakeWallClock
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, WattimDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        wallClock = FakeWallClock(Instant.parse("2026-09-09T12:00:00Z"))
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `default theme is Nord upon initial store readiness`() = testScope.runTest {
        val store = PolicyStore(database, wallClock, this, testDispatcher)
        store.awaitReady()
        advanceUntilIdle()

        val presentation = store.presentationSettings.value
        assertEquals(ThemeId.NORD, presentation.themeId)
        assertEquals("AUTO", presentation.language)
        assertTrue(presentation.showOverlayStats)
        assertEquals(7, presentation.savedSessionMinutes)
    }

    @Test
    fun `every theme can be set and is immediately observed in presentationSettings`() = testScope.runTest {
        val store = PolicyStore(database, wallClock, this, testDispatcher)
        store.awaitReady()
        advanceUntilIdle()

        for (theme in ThemeId.entries) {
            val result = store.setTheme(theme)
            assertTrue("setTheme $theme should succeed", result.isSuccess)
            advanceUntilIdle()

            assertEquals(theme, store.presentationSettings.value.themeId)
            val dbSettings = database.appSettingsDao().getSettings()
            assertEquals(theme.name, dbSettings?.themeId)
        }
    }

    @Test
    fun `theme and settings persist and restore across store recreation`() = testScope.runTest {
        val store1 = PolicyStore(database, wallClock, this, testDispatcher)
        store1.awaitReady()
        advanceUntilIdle()

        store1.setTheme(ThemeId.DRACULA)
        store1.setLanguage("РУССКИЙ")
        store1.setShowOverlayStats(false)
        store1.setSavedSessionMinutes(15)
        advanceUntilIdle()

        // Simulate process recreation with the same database
        val store2 = PolicyStore(database, wallClock, this, testDispatcher)
        store2.awaitReady()
        advanceUntilIdle()

        val restored = store2.presentationSettings.value
        assertEquals(ThemeId.DRACULA, restored.themeId)
        assertEquals("РУССКИЙ", restored.language)
        assertFalse(restored.showOverlayStats)
        assertEquals(15, restored.savedSessionMinutes)
    }

    @Test
    fun `unknown stored theme id safely restores as Nord default`() = testScope.runTest {
        val store1 = PolicyStore(database, wallClock, this, testDispatcher)
        store1.awaitReady()
        advanceUntilIdle()

        // Manually corrupt themeId in database to an unknown value
        val currentSettings = database.appSettingsDao().getSettings()!!
        database.appSettingsDao().setSettings(currentSettings.copy(themeId = "UNKNOWN_CUSTOM_THEME"))

        // Recreate store
        val store2 = PolicyStore(database, wallClock, this, testDispatcher)
        store2.awaitReady()
        advanceUntilIdle()

        assertEquals(ThemeId.NORD, store2.presentationSettings.value.themeId)
    }
}
