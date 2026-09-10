package io.ronesec.android.ui.target

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import io.ronesec.android.R
import io.ronesec.android.ui.MainActivity
import io.ronesec.android.ui.designsystem.TerminalHelpCircle
import io.ronesec.android.ui.designsystem.WattimTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TargetSettingsUiTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun `terminal help circle meets 48dp accessibility touch target and button semantics`() {
        var clicked = false
        composeTestRule.setContent {
            WattimTheme {
                TerminalHelpCircle(
                    onClick = { clicked = true },
                    contentDescriptionText = "Test Help"
                )
            }
        }

        val node = composeTestRule.onNodeWithContentDescription("Test Help")
        node.assertIsDisplayed()
        node.assertWidthIsAtLeast(48.dp)
        node.assertHeightIsAtLeast(48.dp)
        node.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))

        node.performClick()
        assertTrue(clicked)
    }

    @Test
    fun `help circle next to pause duration opens info dialog and dismiss button closes it`() {
        val state = TargetSettingsUiState(
            packageName = "com.example.app",
            displayName = "Example App",
            draft = TargetSettingsDraft(
                packageName = "com.example.app",
                displayName = "Example App",
                enabled = true,
                durationSeconds = 10
            )
        )

        composeTestRule.setContent {
            WattimTheme {
                TargetSettingsScreen(
                    state = state,
                    onBack = {},
                    onPhraseChange = {},
                    onAnimationChange = {},
                    onDurationChange = {},
                    onReinterventionChoice = {},
                    onCustomReinterventionChange = { _, _ -> },
                    onQuickReturnChange = {},
                    onBackoffEnabledChange = {},
                    onBackoffPercentChange = {},
                    onBackoffWindowChange = {},
                    onToggleEnabled = {},
                    onOpenPreview = {},
                    onDismissPreview = {},
                    onSave = {},
                    onRemove = {}
                )
            }
        }

        val context = composeTestRule.activity
        val pauseDurationLabel = context.getString(R.string.target_pause_duration)
        val pauseTitle = context.getString(R.string.help_pause_duration_title).uppercase()
        val pauseDesc = context.getString(R.string.help_pause_duration_desc)
        val dismissText = context.getString(R.string.help_dialog_dismiss).uppercase()

        // Find and click help circle for pause duration
        composeTestRule.onNodeWithContentDescription(pauseDurationLabel)
            .performScrollTo()
            .performClick()

        // Verify dialog is displayed with title, description, and dismiss button
        composeTestRule.onNodeWithText(pauseTitle).assertIsDisplayed()
        composeTestRule.onNodeWithText(pauseDesc).assertIsDisplayed()
        composeTestRule.onNodeWithText(dismissText).assertIsDisplayed()

        // Click dismiss button
        composeTestRule.onNodeWithText(dismissText).performClick()

        // Verify dialog is dismissed
        composeTestRule.onNodeWithText(pauseTitle).assertDoesNotExist()
    }

    @Test
    fun `help circle next to quick lock opens info dialog and dismiss button closes it`() {
        val state = TargetSettingsUiState(
            packageName = "com.example.app",
            displayName = "Example App",
            canQuickLock = true,
            draft = TargetSettingsDraft(
                packageName = "com.example.app",
                displayName = "Example App"
            )
        )

        composeTestRule.setContent {
            WattimTheme {
                TargetSettingsScreen(
                    state = state,
                    onBack = {},
                    onPhraseChange = {},
                    onAnimationChange = {},
                    onDurationChange = {},
                    onReinterventionChoice = {},
                    onCustomReinterventionChange = { _, _ -> },
                    onQuickReturnChange = {},
                    onBackoffEnabledChange = {},
                    onBackoffPercentChange = {},
                    onBackoffWindowChange = {},
                    onToggleEnabled = {},
                    onOpenPreview = {},
                    onDismissPreview = {},
                    onSave = {},
                    onRemove = {}
                )
            }
        }

        val context = composeTestRule.activity
        val quickLockDesc = context.getString(R.string.help_quick_lock_desc)
        val dismissText = context.getString(R.string.help_dialog_dismiss).uppercase()

        // Scroll to and click quick lock help
        composeTestRule.onNodeWithContentDescription(context.getString(R.string.help_quick_lock_title))
            .performScrollTo()
            .performClick()

        // Verify description is displayed
        composeTestRule.onNodeWithText(quickLockDesc).assertIsDisplayed()

        // Dismiss
        composeTestRule.onNodeWithText(dismissText).performClick()
        composeTestRule.onNodeWithText(quickLockDesc).assertDoesNotExist()
    }

    @Test
    fun `help circles for reintervention, quick return, and exponential backoff open dialogs and dismiss`() {
        val state = TargetSettingsUiState(
            packageName = "com.example.app",
            displayName = "Example App",
            draft = TargetSettingsDraft(
                packageName = "com.example.app",
                displayName = "Example App",
                enabled = true,
                backoffEnabled = true
            )
        )

        composeTestRule.setContent {
            WattimTheme {
                TargetSettingsScreen(
                    state = state,
                    onBack = {},
                    onPhraseChange = {},
                    onAnimationChange = {},
                    onDurationChange = {},
                    onReinterventionChoice = {},
                    onCustomReinterventionChange = { _, _ -> },
                    onQuickReturnChange = {},
                    onBackoffEnabledChange = {},
                    onBackoffPercentChange = {},
                    onBackoffWindowChange = {},
                    onToggleEnabled = {},
                    onOpenPreview = {},
                    onDismissPreview = {},
                    onSave = {},
                    onRemove = {}
                )
            }
        }

        val context = composeTestRule.activity
        val dismissText = context.getString(R.string.help_dialog_dismiss).uppercase()

        // 1. Re-intervention
        val reintLabel = context.getString(R.string.reintervention_label)
        val reintDesc = context.getString(R.string.help_reintervention_desc)
        composeTestRule.onNodeWithContentDescription(reintLabel).performScrollTo().performClick()
        composeTestRule.onNodeWithText(reintDesc).assertIsDisplayed()
        composeTestRule.onNodeWithText(dismissText).performClick()
        composeTestRule.onNodeWithText(reintDesc).assertDoesNotExist()

        // 2. Quick Return Grace
        val qrLabel = context.getString(R.string.quick_return_label)
        val qrDesc = context.getString(R.string.help_quick_return_desc)
        composeTestRule.onNodeWithContentDescription(qrLabel).performScrollTo().performClick()
        composeTestRule.onNodeWithText(qrDesc).assertIsDisplayed()
        composeTestRule.onNodeWithText(dismissText).performClick()
        composeTestRule.onNodeWithText(qrDesc).assertDoesNotExist()

        // 3. Exponential Backoff Growth
        val growthLabel = context.getString(R.string.growth_label)
        val growthDesc = context.getString(R.string.help_backoff_desc)
        composeTestRule.onNodeWithContentDescription(growthLabel).performScrollTo().performClick()
        composeTestRule.onNodeWithText(growthDesc).assertIsDisplayed()
        composeTestRule.onNodeWithText(dismissText).performClick()
        composeTestRule.onNodeWithText(growthDesc).assertDoesNotExist()
    }
}
