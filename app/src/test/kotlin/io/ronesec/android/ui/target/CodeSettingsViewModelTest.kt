package io.ronesec.android.ui.target

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.ronesec.android.data.PolicyStore
import io.ronesec.android.data.WattimDatabase
import io.ronesec.domain.model.FakeWallClock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class CodeSettingsViewModelTest {
    @Test
    fun settingsAreIndependentBoundedAndRestoredFromSavedTarget() = runTest {
        val database = WattimDatabase.createInMemory(ApplicationProvider.getApplicationContext<Context>())
        try {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val store = PolicyStore(database, FakeWallClock(Instant.EPOCH), backgroundScope, dispatcher)
            store.awaitReady()
            fun viewModel() = TargetSettingsViewModel(
                packageName = "sample.app", policyStore = store,
                coroutineScope = backgroundScope, ioDispatcher = dispatcher
            )
            val model = viewModel()
            model.onTwoStageUnlockChange(true)
            assertFalse(model.uiState.value.draft.requireEmergencyCode)
            model.onRequireEmergencyCodeChange(true)
            model.onTwoStageUnlockChange(false)
            assertTrue(model.uiState.value.draft.requireEmergencyCode)
            model.onUnlockCodeLengthChange(0)
            assertEquals(1, model.uiState.value.draft.unlockCodeLength)
            model.onUnlockCodeLengthChange(100)
            assertEquals(10, model.uiState.value.draft.unlockCodeLength)
            model.onSave()
            model.uiState.first { it.isSaved }
            val restored = viewModel().uiState.value.draft
            assertFalse(restored.twoStageUnlock)
            assertTrue(restored.requireEmergencyCode)
            assertEquals(10, restored.unlockCodeLength)
        } finally {
            database.close()
        }
    }
}
