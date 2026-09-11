package io.ronesec.android.ui.intervention

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.ronesec.android.ui.MainActivity
import io.ronesec.android.ui.designsystem.ThemeId
import io.ronesec.android.ui.designsystem.ThemeRegistry
import io.ronesec.android.ui.designsystem.WattimTheme
import io.ronesec.domain.breathing.BreathingTimeline
import io.ronesec.domain.model.AnimationMode
import io.ronesec.domain.policy.EffectiveInterventionConfig
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class T14_InterventionContentAndTimelineTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    private val testConfig = EffectiveInterventionConfig(
        packageName = "com.example.social",
        displayName = "Social App",
        phrase = "Take a breath and pause",
        animation = AnimationMode.FILL,
        durationMs = 5000L,
        reinterventionMs = 300000L,
        quickReturnGraceMs = 15000L,
        baseDurationMs = 5000L,
        backoffExponent = 1
    )

    @Test
    fun `during breathing displays phase, countdown, exit and emergency buttons, but not continue or cancel`() {
        var exitClicked = false
        var emergencyClicked = false

        // 1250ms into 5000ms duration -> 25% complete, INHALE
        val progress = BreathingTimeline.calculate(
            startElapsedMs = 10000L,
            nowElapsedMs = 11250L,
            durationMs = 5000L
        )

        composeTestRule.setContent {
            WattimTheme {
                InterventionContent(
                    config = testConfig,
                    progress = progress,
                    showSavedBadge = false,
                    savedMinutes = 0L,
                    isEmergencyDialogOpen = false,
                    onContinue = {},
                    onExit = { exitClicked = true },
                    onCancel = {},
                    onEmergencyClick = { emergencyClicked = true },
                    onDismissEmergency = {},
                    onEmergencyOnce = {},
                    onEmergencyTimed = {},
                    onEmergencyForever = {}
                )
            }
        }

        // Target title
        composeTestRule.onNodeWithText("SOCIAL APP").assertIsDisplayed()
        // Phrase
        composeTestRule.onNodeWithText("Take a breath and pause").assertIsDisplayed()
        // Phase
        composeTestRule.onNodeWithText("INHALE").assertIsDisplayed()
        // Countdown
        composeTestRule.onNodeWithText(progress.formattedCountdown).assertIsDisplayed()

        // Action buttons
        composeTestRule.onNodeWithText("EXIT").assertIsDisplayed()
        composeTestRule.onNodeWithText("EMERGENCY", substring = true).assertIsDisplayed()

        // Continue and Cancel should not exist
        composeTestRule.onNodeWithText("CONTINUE").assertDoesNotExist()
        composeTestRule.onNodeWithText("CANCEL").assertDoesNotExist()

        // Test button clicks
        composeTestRule.onNodeWithText("EXIT").performClick()
        assertTrue("Exit should have been clicked", exitClicked)

        composeTestRule.onNodeWithText("EMERGENCY", substring = true).performClick()
        assertTrue("Emergency should have been clicked", emergencyClicked)
    }

    @Test
    fun `after breathing completes displays continue and cancel buttons, but not exit or emergency`() {
        var continueClicked = false
        var cancelClicked = false

        // 5000ms into 5000ms duration -> COMPLETE
        val progress = BreathingTimeline.calculate(
            startElapsedMs = 10000L,
            nowElapsedMs = 15000L,
            durationMs = 5000L
        )

        composeTestRule.setContent {
            WattimTheme {
                InterventionContent(
                    config = testConfig,
                    progress = progress,
                    showSavedBadge = true,
                    savedMinutes = 42L,
                    isEmergencyDialogOpen = false,
                    onContinue = { continueClicked = true },
                    onExit = {},
                    onCancel = { cancelClicked = true },
                    onEmergencyClick = {},
                    onDismissEmergency = {},
                    onEmergencyOnce = {},
                    onEmergencyTimed = {},
                    onEmergencyForever = {}
                )
            }
        }

        // Target title
        composeTestRule.onNodeWithText("SOCIAL APP").assertIsDisplayed()
        // Saved badge
        composeTestRule.onNodeWithContentDescription("Saved time badge: 42 MIN").assertIsDisplayed()
        // Complete phase
        composeTestRule.onNodeWithText("COMPLETE").assertIsDisplayed()

        // Action buttons
        composeTestRule.onNodeWithText("CONTINUE").assertIsDisplayed()
        composeTestRule.onNodeWithText("CANCEL").assertIsDisplayed()

        // Exit and Emergency should not exist
        composeTestRule.onNodeWithText("EXIT").assertDoesNotExist()
        composeTestRule.onNodeWithText("EMERGENCY").assertDoesNotExist()

        // Click actions
        composeTestRule.onNodeWithText("CONTINUE").performClick()
        assertTrue("Continue should have been clicked", continueClicked)

        composeTestRule.onNodeWithText("CANCEL").performClick()
        assertTrue("Cancel should have been clicked", cancelClicked)
    }

    @Test
    fun `wave mode hides remaining time while preserving breathing actions`() {
        val progress = BreathingTimeline.calculate(
            startElapsedMs = 10_000L,
            nowElapsedMs = 11_250L,
            durationMs = 5_000L
        )

        composeTestRule.setContent {
            WattimTheme {
                InterventionContent(
                    config = testConfig.copy(animation = AnimationMode.WAVE),
                    progress = progress,
                    showSavedBadge = false,
                    savedMinutes = 0L,
                    isEmergencyDialogOpen = false,
                    onContinue = {},
                    onExit = {},
                    onCancel = {},
                    onEmergencyClick = {},
                    onDismissEmergency = {},
                    onEmergencyOnce = {},
                    onEmergencyTimed = {},
                    onEmergencyForever = {}
                )
            }
        }

        composeTestRule.onNodeWithText(progress.formattedCountdown).assertDoesNotExist()
        composeTestRule.onNodeWithText("INHALE").assertDoesNotExist()
        composeTestRule.onNodeWithText("EXHALE").assertDoesNotExist()
        composeTestRule.onNodeWithText("EXIT").assertIsDisplayed()
        composeTestRule.onNodeWithText("EMERGENCY", substring = true).assertIsDisplayed()
    }

    @Test
    fun `fill_2 mode hides remaining time and breathing phase text during breathing`() {
        val progress = BreathingTimeline.calculate(
            startElapsedMs = 10_000L,
            nowElapsedMs = 11_250L,
            durationMs = 5_000L
        )

        composeTestRule.setContent {
            WattimTheme {
                InterventionContent(
                    config = testConfig.copy(animation = AnimationMode.FILL_2),
                    progress = progress,
                    showSavedBadge = false,
                    savedMinutes = 0L,
                    isEmergencyDialogOpen = false,
                    onContinue = {},
                    onExit = {},
                    onCancel = {},
                    onEmergencyClick = {},
                    onDismissEmergency = {},
                    onEmergencyOnce = {},
                    onEmergencyTimed = {},
                    onEmergencyForever = {}
                )
            }
        }

        composeTestRule.onNodeWithText(progress.formattedCountdown).assertDoesNotExist()
        composeTestRule.onNodeWithText("INHALE").assertDoesNotExist()
        composeTestRule.onNodeWithText("EXHALE").assertDoesNotExist()
        composeTestRule.onNodeWithText("EXIT").assertIsDisplayed()
        composeTestRule.onNodeWithText("EMERGENCY", substring = true).assertIsDisplayed()
    }

    @Test
    fun `untimed animation shows COMPLETE phase text when breathing is complete`() {
        val progress = BreathingTimeline.calculate(
            startElapsedMs = 10_000L,
            nowElapsedMs = 15_000L,
            durationMs = 5_000L
        )

        composeTestRule.setContent {
            WattimTheme {
                InterventionContent(
                    config = testConfig.copy(animation = AnimationMode.FILL_2),
                    progress = progress,
                    showSavedBadge = false,
                    savedMinutes = 0L,
                    isEmergencyDialogOpen = false,
                    onContinue = {},
                    onExit = {},
                    onCancel = {},
                    onEmergencyClick = {},
                    onDismissEmergency = {},
                    onEmergencyOnce = {},
                    onEmergencyTimed = {},
                    onEmergencyForever = {}
                )
            }
        }

        composeTestRule.onNodeWithText("COMPLETE").assertIsDisplayed()
        composeTestRule.onNodeWithText(progress.formattedCountdown).assertDoesNotExist()
    }

    @Test
    fun `renders without crash in all animation modes across all 6 themes`() {
        composeTestRule.setContent {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                for (theme in ThemeId.entries) {
                    for (anim in AnimationMode.entries) {
                        val config = testConfig.copy(animation = anim)
                        val progress = BreathingTimeline.calculate(
                            startElapsedMs = 10000L,
                            nowElapsedMs = 13000L,
                            durationMs = 5000L
                        )

                        WattimTheme(colors = ThemeRegistry.getColors(theme)) {
                            InterventionContent(
                                config = config,
                                progress = progress,
                                showSavedBadge = false,
                                savedMinutes = 0L,
                                isEmergencyDialogOpen = false,
                                onContinue = {},
                                onExit = {},
                                onCancel = {},
                                onEmergencyClick = {},
                                onDismissEmergency = {},
                                onEmergencyOnce = {},
                                onEmergencyTimed = {},
                                onEmergencyForever = {},
                                reducedMotion = (anim == AnimationMode.PULSE)
                            )
                        }
                    }
                }
            }
        }

        composeTestRule.onAllNodes(androidx.compose.ui.test.hasText("SOCIAL APP"))[0].assertIsDisplayed()
        composeTestRule.onAllNodes(androidx.compose.ui.test.hasText("EXHALE"))[0].assertIsDisplayed()
    }
}
