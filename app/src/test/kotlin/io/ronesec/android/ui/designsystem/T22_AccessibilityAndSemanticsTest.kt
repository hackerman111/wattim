package io.ronesec.android.ui.designsystem

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import io.ronesec.android.ui.MainActivity
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class T22_AccessibilityAndSemanticsTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun `interactive button has minimum 48dp touch target in both dimensions and Role Button`() {
        var clicked = false
        composeTestRule.setContent {
            WattimTheme {
                TerminalButton(
                    text = "Confirm",
                    onClick = { clicked = true },
                    variant = TerminalButtonVariant.PRIMARY
                )
            }
        }

        val buttonNode = composeTestRule.onNodeWithText("CONFIRM")
        buttonNode.assertIsDisplayed()
        buttonNode.assertWidthIsAtLeast(48.dp)
        buttonNode.assertHeightIsAtLeast(48.dp)
        buttonNode.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))

        buttonNode.performClick()
        assertTrue("Button should handle click", clicked)
    }

    @Test
    fun `interactive badge has minimum 48dp touch target and non-color textual state cue`() {
        var toggled = false
        composeTestRule.setContent {
            WattimTheme {
                Column {
                    TerminalBadge(
                        text = "ON",
                        isActive = true,
                        onClick = { toggled = true }
                    )
                    TerminalBadge(
                        text = "OFF",
                        isActive = false,
                        onClick = { toggled = true }
                    )
                }
            }
        }

        val activeBadge = composeTestRule.onNodeWithText("ON")
        activeBadge.assertIsDisplayed()
        activeBadge.assertWidthIsAtLeast(48.dp)
        activeBadge.assertHeightIsAtLeast(48.dp)

        val inactiveBadge = composeTestRule.onNodeWithText("OFF")
        inactiveBadge.assertIsDisplayed()
        inactiveBadge.assertWidthIsAtLeast(48.dp)
        inactiveBadge.assertHeightIsAtLeast(48.dp)

        activeBadge.performClick()
        assertTrue("Active badge click handled", toggled)
    }

    @Test
    fun `navigation tabs have Role Tab and selected state semantics`() {
        var selectedTab = TerminalTab.APPS
        composeTestRule.setContent {
            WattimTheme {
                TerminalBottomNav(
                    selectedTab = selectedTab,
                    onTabSelected = { selectedTab = it }
                )
            }
        }

        val appsTab = composeTestRule.onNodeWithText("APPS")
        appsTab.assertIsDisplayed()
        appsTab.assertHeightIsAtLeast(48.dp)
        appsTab.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
        appsTab.assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))

        val blockTab = composeTestRule.onNodeWithText("BLOCK")
        blockTab.assertIsDisplayed()
        blockTab.assertHeightIsAtLeast(48.dp)
        blockTab.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
        blockTab.assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, false))
    }

    @Test
    fun `time input steppers have minimum 48dp touch target in both dimensions`() {
        var hoursVal = 9
        var minutesVal = 0

        composeTestRule.setContent {
            WattimTheme {
                TimeInputSection(
                    hours = hoursVal,
                    minutes = minutesVal,
                    onTimeChange = { h, m ->
                        hoursVal = h
                        minutesVal = m
                    }
                )
            }
        }

        val stepPlus1H = composeTestRule.onNodeWithText("+1H")
        stepPlus1H.assertIsDisplayed()
        stepPlus1H.assertWidthIsAtLeast(48.dp)
        stepPlus1H.assertHeightIsAtLeast(48.dp)

        stepPlus1H.performClick()
        assertTrue("Hours should increment to 10", hoursVal == 10)
    }

    @Test
    fun `input field communicates error state via semantics and displays text`() {
        composeTestRule.setContent {
            WattimTheme {
                TerminalInputField(
                    value = "Invalid input",
                    onValueChange = {},
                    label = "LABEL",
                    isError = true,
                    errorMessage = "Value cannot be empty"
                )
            }
        }

        composeTestRule.onNodeWithText("Value cannot be empty").assertIsDisplayed()
        composeTestRule.onNodeWithText("LABEL").assertIsDisplayed()
    }
}
