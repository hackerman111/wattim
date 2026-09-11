package io.ronesec.android.ui.designsystem

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import io.ronesec.android.ui.MainActivity
import io.ronesec.domain.model.AnimationMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class T18_AllThemeVisualStateMatrixTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun `all six themes provide distinct token palettes and non-empty semantic colors`() {
        val palettes = ThemeId.entries.map { it to ThemeRegistry.getColors(it) }
        assertEquals(6, palettes.size)

        for (i in palettes.indices) {
            for (j in i + 1 until palettes.size) {
                val (theme1, colors1) = palettes[i]
                val (theme2, colors2) = palettes[j]
                assertNotEquals("Theme $theme1 and $theme2 should have distinct backgrounds", colors1.background, colors2.background)
                assertNotEquals("Theme $theme1 and $theme2 should have distinct accents", colors1.accent, colors2.accent)
            }
        }
    }

    @Test
    fun `wave animation repeats independently on its own cycle`() {
        val start = BreathingGeometry.wavePhaseRadians(0L)
        val halfway = BreathingGeometry.wavePhaseRadians(BreathingGeometry.WAVE_CYCLE_MS / 2L)
        val nextCycle = BreathingGeometry.wavePhaseRadians(BreathingGeometry.WAVE_CYCLE_MS)

        assertNotEquals(start, halfway)
        assertEquals(start, nextCycle, 0.0001f)
    }

    @Test
    fun `fill_2 height stays within bounds during breathing and varies over time`() {
        val heights = (0L..10_000L step 400L).map { elapsed ->
            BreathingGeometry.fill2HeightFraction(elapsedMs = elapsed, progress = 0.5f, reducedMotion = false)
        }
        for (h in heights) {
            assertTrue("Height $h should be >= 0.15f", h >= 0.15f - 0.0001f)
            assertTrue("Height $h should be <= 0.85f", h <= 0.85f + 0.0001f)
        }
        val distinctValues = heights.distinct()
        assertTrue("Height should vary over time, got ${distinctValues.size} distinct values", distinctValues.size > 1)

        var hasIncreased = false
        var hasDecreased = false
        for (i in 0 until heights.size - 1) {
            if (heights[i + 1] > heights[i] + 0.001f) hasIncreased = true
            if (heights[i + 1] < heights[i] - 0.001f) hasDecreased = true
        }
        assertTrue("Fill 2 should increase at some points", hasIncreased)
        assertTrue("Fill 2 should decrease at some points", hasDecreased)
    }

    @Test
    fun `fill_2 height reaches 1_0 on completion`() {
        val completed = BreathingGeometry.fill2HeightFraction(elapsedMs = 4000L, progress = 1.0f, reducedMotion = false)
        assertEquals(1.0f, completed, 0.0001f)
    }

    @Test
    fun `fill_2 height is fixed at 0_5 on reduced motion`() {
        val reduced = BreathingGeometry.fill2HeightFraction(elapsedMs = 4000L, progress = 0.5f, reducedMotion = true)
        assertEquals(0.5f, reduced, 0.0001f)
    }

    @Test
    fun `exercise full theme matrix across all terminal components and states`() {
        composeTestRule.setContent {
            androidx.compose.foundation.layout.Column(
                modifier = androidx.compose.ui.Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())
            ) {
                for (theme in ThemeId.entries) {
                    WattimTheme(themeId = theme) {
                        Column {
                            // TerminalCard states
                            TerminalCard(isError = false) {
                                androidx.compose.material3.Text("Normal Card ${theme.name}")
                            }
                            TerminalCard(isError = true) {
                                androidx.compose.material3.Text("Error Card ${theme.name}")
                            }

                            // TerminalButton states
                            TerminalButton(
                                text = "Primary ${theme.name}",
                                onClick = {},
                                variant = TerminalButtonVariant.PRIMARY,
                                enabled = true
                            )
                            TerminalButton(
                                text = "Disabled ${theme.name}",
                                onClick = {},
                                variant = TerminalButtonVariant.SECONDARY,
                                enabled = false
                            )
                            TerminalButton(
                                text = "Danger ${theme.name}",
                                onClick = {},
                                variant = TerminalButtonVariant.DANGER,
                                enabled = true
                            )

                            // TerminalBadge states
                            TerminalBadge(text = "Active ${theme.name}", isActive = true)
                            TerminalBadge(text = "Inactive ${theme.name}", isActive = false)

                            // TerminalInputField states
                            TerminalInputField(
                                value = "Test Input",
                                onValueChange = {},
                                label = "LABEL ${theme.name}",
                                isError = false
                            )
                            TerminalInputField(
                                value = "Error Input",
                                onValueChange = {},
                                label = "ERROR ${theme.name}",
                                isError = true,
                                errorMessage = "Validation error ${theme.name}"
                            )

                            // TerminalBottomNav
                            TerminalBottomNav(
                                selectedTab = TerminalTab.APPS,
                                onTabSelected = {}
                            )

                            // TimeInputSection
                            TimeInputSection(
                                hours = 9,
                                minutes = 30,
                                onTimeChange = { _, _ -> }
                            )
                        }
                    }
                }
            }
        }

        for (theme in ThemeId.entries) {
            composeTestRule.onNodeWithText("Normal Card ${theme.name}").assertExists()
            composeTestRule.onNodeWithText("Error Card ${theme.name}").assertExists()
            composeTestRule.onNodeWithText("PRIMARY ${theme.name}").assertExists().assertIsEnabled()
            composeTestRule.onNodeWithText("DISABLED ${theme.name}").assertExists().assertIsNotEnabled()
            composeTestRule.onNodeWithText("DANGER ${theme.name}").assertExists().assertIsEnabled()
            composeTestRule.onNodeWithText("Active ${theme.name}").assertExists()
            composeTestRule.onNodeWithText("Inactive ${theme.name}").assertExists()
            composeTestRule.onNodeWithText("LABEL ${theme.name}").assertExists()
            composeTestRule.onNodeWithText("Validation error ${theme.name}").assertExists()
        }
    }

    @Test
    fun `exercise all animation modes across all six themes including reduced motion`() {
        val animationStyles = AnimationMode.entries

        composeTestRule.setContent {
            androidx.compose.foundation.layout.Column(
                modifier = androidx.compose.ui.Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())
            ) {
                for (theme in ThemeId.entries) {
                    WattimTheme(themeId = theme) {
                        Column {
                            for (style in animationStyles) {
                                BreathingCanvas(
                                    style = style,
                                    progress = 0.65f,
                                    elapsedMs = 4000L,
                                    modifier = Modifier.size(100.dp, 100.dp),
                                    reducedMotion = false,
                                    contentDesc = "Breathing ${theme.name} ${style.name}"
                                )
                                BreathingCanvas(
                                    style = style,
                                    progress = 0.65f,
                                    elapsedMs = 4000L,
                                    modifier = Modifier.size(100.dp, 100.dp),
                                    reducedMotion = true,
                                    contentDesc = "Breathing Reduced ${theme.name} ${style.name}"
                                )
                            }
                        }
                    }
                }
            }
        }

        for (theme in ThemeId.entries) {
            for (style in animationStyles) {
                composeTestRule.onNodeWithContentDescription("Breathing ${theme.name} ${style.name}").assertExists()
                composeTestRule.onNodeWithContentDescription("Breathing Reduced ${theme.name} ${style.name}").assertExists()
            }
        }
    }
}
