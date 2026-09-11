package io.ronesec.android.ui.intervention

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import io.ronesec.android.ui.MainActivity
import io.ronesec.android.ui.designsystem.WattimTheme
import io.ronesec.domain.breathing.BreathingTimeline
import io.ronesec.domain.model.AnimationMode
import io.ronesec.domain.policy.EffectiveInterventionConfig
import io.ronesec.domain.protection.AttentionCheckUi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AttentionCheckContentTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    private val sampleConfig = EffectiveInterventionConfig(
        packageName = "com.example.social",
        displayName = "Social App",
        phrase = "Mindful pause",
        animation = AnimationMode.PULSE,
        durationMs = 10000L,
        reinterventionMs = 0L,
        quickReturnGraceMs = 0L,
        baseDurationMs = 10000L,
        backoffExponent = 0
    )

    @Test
    fun displaysAttentionCheckPromptAndCode() {
        val attentionCheck = AttentionCheckUi(
            active = true,
            code = "7492",
            deadlineElapsedMs = 10000L,
            timeoutMs = 5000L,
            pausedElapsedProgressMs = 3000L,
            totalDurationMs = 10000L,
            hasError = false
        )
        val pausedProgress = BreathingTimeline.calculate(0L, 3000L, 10000L)

        composeTestRule.setContent {
            WattimTheme {
                AttentionCheckContent(
                    config = sampleConfig,
                    pausedProgress = pausedProgress,
                    attentionCheck = attentionCheck,
                    onSubmit = {},
                    onExit = {},
                    onEmergency = {},
                    nowElapsedMs = 5000L
                )
            }
        }

        composeTestRule.onNodeWithText("ATTENTION CHECK").assertIsDisplayed()
        composeTestRule.onNodeWithText("7 4 9 2").assertIsDisplayed()
        composeTestRule.onNodeWithText("EXIT").assertIsDisplayed()
        composeTestRule.onNodeWithText("EMERGENCY", substring = true).assertIsDisplayed()
    }

    @Test
    fun autoSubmitsWhenFullCodeEntered() {
        val attentionCheck = AttentionCheckUi(
            active = true,
            code = "8134",
            deadlineElapsedMs = 10000L,
            timeoutMs = 5000L,
            pausedElapsedProgressMs = 3000L,
            totalDurationMs = 10000L,
            hasError = false
        )
        val pausedProgress = BreathingTimeline.calculate(0L, 3000L, 10000L)
        var submittedCode: String? = null

        composeTestRule.setContent {
            WattimTheme {
                AttentionCheckContent(
                    config = sampleConfig,
                    pausedProgress = pausedProgress,
                    attentionCheck = attentionCheck,
                    onSubmit = { submittedCode = it },
                    onExit = {},
                    onEmergency = {},
                    nowElapsedMs = 5000L
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Type the code to continue:")
            .performTextInput("8134")

        assertEquals("8134", submittedCode)
    }

    @Test
    fun displaysErrorWhenAttentionCheckHasError() {
        val attentionCheck = AttentionCheckUi(
            active = true,
            code = "9951",
            deadlineElapsedMs = 10000L,
            timeoutMs = 5000L,
            pausedElapsedProgressMs = 3000L,
            totalDurationMs = 10000L,
            hasError = true
        )
        val pausedProgress = BreathingTimeline.calculate(0L, 3000L, 10000L)

        composeTestRule.setContent {
            WattimTheme {
                AttentionCheckContent(
                    config = sampleConfig,
                    pausedProgress = pausedProgress,
                    attentionCheck = attentionCheck,
                    onSubmit = {},
                    onExit = {},
                    onEmergency = {},
                    nowElapsedMs = 5000L
                )
            }
        }

        composeTestRule.onNodeWithText("Incorrect code").assertIsDisplayed()
    }

    @Test
    fun clickingExitAndEmergencyInvokesCallbacks() {
        val attentionCheck = AttentionCheckUi(
            active = true,
            code = "1234",
            deadlineElapsedMs = 10000L,
            timeoutMs = 5000L,
            pausedElapsedProgressMs = 3000L,
            totalDurationMs = 10000L,
            hasError = false
        )
        val pausedProgress = BreathingTimeline.calculate(0L, 3000L, 10000L)
        var exitCalled = false
        var emergencyCalled = false

        composeTestRule.setContent {
            WattimTheme {
                AttentionCheckContent(
                    config = sampleConfig,
                    pausedProgress = pausedProgress,
                    attentionCheck = attentionCheck,
                    onSubmit = {},
                    onExit = { exitCalled = true },
                    onEmergency = { emergencyCalled = true },
                    nowElapsedMs = 5000L
                )
            }
        }

        composeTestRule.onNodeWithText("EXIT").performClick()
        assertTrue(exitCalled)

        composeTestRule.onNodeWithText("EMERGENCY", substring = true).performClick()
        assertTrue(emergencyCalled)
    }
}
