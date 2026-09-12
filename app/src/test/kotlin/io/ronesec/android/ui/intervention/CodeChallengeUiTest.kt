package io.ronesec.android.ui.intervention

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import io.ronesec.android.ui.MainActivity
import io.ronesec.android.ui.designsystem.WattimTheme
import io.ronesec.android.ui.codes.CodesPanelState
import io.ronesec.android.ui.codes.CodesScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CodeChallengeUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun `active Codes panel acknowledges that the code is visible`() {
        var visible: Triple<io.ronesec.domain.model.SessionId, Int, Long>? = null
        val sessionId = io.ronesec.domain.model.SessionId(1, 2, 3)
        composeRule.setContent {
            WattimTheme {
                CodesScreen(
                    state = CodesPanelState.Active(sessionId, 4, 9L, "Target", "0123"),
                    onCodeVisible = { id, cycle, revision -> visible = Triple(id, cycle, revision) }
                )
            }
        }
        composeRule.waitForIdle()
        assertEquals(Triple(sessionId, 4, 9L), visible)
    }

    @Test
    fun `code gate exposes generation and emergency before breathing`() {
        var generated = false
        var emergency = false
        composeRule.setContent {
            WattimTheme {
                CodeGateContent(
                    targetName = "Target",
                    generated = false,
                    length = 6,
                    error = false,
                    emergencyCode = "1234567890",
                    onGenerate = { generated = true },
                    onSubmit = {},
                    onExit = {},
                    onEmergency = { emergency = true }
                )
            }
        }

        composeRule.onNodeWithText("GENERATE CODE").performClick()
        composeRule.onNodeWithText("EMERGENCY", substring = true).performClick()
        assertTrue(generated)
        assertTrue(emergency)
    }

    @Test
    fun `emergency code acts as safety fuse in EmergencyDialog`() {
        var submittedDuration = 0L
        var submittedCode = ""
        var onceCode = ""
        composeRule.setContent {
            WattimTheme {
                EmergencyDialog(
                    targetName = "Target",
                    onDismissRequest = {},
                    onEmergencyOnce = {},
                    onEmergencyTimed = {},
                    onEmergencyForever = {},
                    requireCode = true,
                    emergencyCode = "1234567890",
                    onOnceWithCode = { onceCode = it },
                    onTimedWithCode = { duration, code ->
                        submittedDuration = duration
                        submittedCode = code
                    }
                )
            }
        }

        // Code banner is displayed inside the dialog
        composeRule.onNodeWithText("Emergency access code: 1234567890").assertIsDisplayed()

        // Initially fuse is locked: clicking buttons does nothing
        composeRule.onNodeWithText("ENTER ONCE").performClick()
        assertEquals("", onceCode)
        composeRule.onNodeWithText("15 MIN").performClick()
        assertEquals(0L, submittedDuration)

        // Type matching 10 digits
        composeRule.onNodeWithContentDescription("Enter the 10-digit code from the intervention screen")
            .performTextInput("1234567890")

        // Now fuse is unlocked: clicking triggers actions with code
        composeRule.onNodeWithText("15 MIN").performClick()
        assertEquals(15L * 60L * 1000L, submittedDuration)
        assertEquals("1234567890", submittedCode)

        composeRule.onNodeWithText("ENTER ONCE").performClick()
        assertEquals("1234567890", onceCode)
    }

    @Test
    fun `SessionInterventionContent freezes progress and suppresses attention check during emergency`() {
        val session = io.ronesec.domain.model.SessionId(1, 1, 1)
        val config = io.ronesec.domain.policy.EffectiveInterventionConfig(
            packageName = "com.example.target",
            displayName = "Target App",
            phrase = "Pause",
            animation = io.ronesec.domain.model.AnimationMode.FILL,
            durationMs = 10000L,
            reinterventionMs = 0L,
            quickReturnGraceMs = 0L,
            baseDurationMs = 10000L,
            backoffExponent = 0,
            requireEmergencyCode = true
        )
        val challenge = io.ronesec.domain.protection.CodeChallengeUi(
            gate = false,
            generated = false,
            unlockRequestRevision = 0L,
            codeLength = 4,
            emergencyCode = "9876543210",
            error = false,
            breathingStartElapsedMs = null,
            isEmergency = true,
            pausedProgressMs = 3000L,
            attentionCheck = io.ronesec.domain.protection.AttentionCheckUi(
                active = true,
                code = "1111",
                deadlineElapsedMs = 5000L,
                timeoutMs = 5000L,
                pausedElapsedProgressMs = 3000L,
                totalDurationMs = 10000L
            )
        )
        val mode = io.ronesec.android.platform.overlay.OverlayMode.Intervention(
            sessionId = session,
            cycle = 1,
            config = config,
            isComplete = false,
            challenge = challenge
        )
        val uiState = io.ronesec.android.platform.overlay.OverlayUiState(
            mode = mode,
            isEmergencyDialogOpen = true
        )
        val clock = object : io.ronesec.domain.model.MonotonicClock {
            override fun elapsedRealtimeMs(): Long = 25000L
        }
        val presenter = io.ronesec.android.platform.overlay.OverlayPresenter(
            actionDispatcher = object : io.ronesec.android.platform.overlay.OverlayActionDispatcher {
                override fun onContinue(sessionId: io.ronesec.domain.model.SessionId, cycle: Int) {}
                override fun onExit(sessionId: io.ronesec.domain.model.SessionId?) {}
                override fun onCancel(sessionId: io.ronesec.domain.model.SessionId?) {}
                override fun onBreathingDeadlineReached(sessionId: io.ronesec.domain.model.SessionId, cycle: Int) {}
                override fun onEmergencyOnce(sessionId: io.ronesec.domain.model.SessionId, cycle: Int) {}
                override fun onEmergencyTimed(sessionId: io.ronesec.domain.model.SessionId, cycle: Int, durationMs: Long) {}
                override fun onEmergencyForever(sessionId: io.ronesec.domain.model.SessionId, cycle: Int) {}
            },
            policyStore = null,
            statisticsStore = null,
            scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined)
        )

        composeRule.setContent {
            WattimTheme {
                SessionInterventionContent(
                    mode = mode,
                    uiState = uiState,
                    presenter = presenter,
                    monotonicClock = clock
                )
            }
        }

        // Emergency Dialog is displayed
        composeRule.onNodeWithText("ARE YOU SURE?").assertIsDisplayed()
        composeRule.onNodeWithText("Emergency access code: 9876543210").assertIsDisplayed()

        // Attention check is suppressed!
        composeRule.onNodeWithText("ATTENTION CHECK").assertDoesNotExist()
        composeRule.onNodeWithText("1 1 1 1").assertDoesNotExist()
    }
}
