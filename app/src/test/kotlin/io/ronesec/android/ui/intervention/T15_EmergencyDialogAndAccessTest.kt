package io.ronesec.android.ui.intervention

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import io.ronesec.android.ui.MainActivity
import io.ronesec.android.ui.designsystem.WattimTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
class T15_EmergencyDialogAndAccessTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun `emergency dialog displays target heading, confirmation, timed options and return action`() {
        var dismissCount = 0
        var timedDuration: Long? = null
        var foreverClicked = false

        composeTestRule.setContent {
            WattimTheme {
                EmergencyDialog(
                    targetName = "YOUTUBE",
                    onDismissRequest = { dismissCount++ },
                    onEmergencyOnce = {},
                    onEmergencyTimed = { timedDuration = it },
                    onEmergencyForever = { foreverClicked = true }
                )
            }
        }

        // Target heading
        composeTestRule.onNodeWithText("YOUTUBE", substring = true).assertIsDisplayed()
        // Dialog title
        composeTestRule.onNodeWithText("ARE YOU SURE?").assertIsDisplayed()
        // Message
        composeTestRule.onNodeWithText("Are you sure you want to skip the mindful breathing pause? Choose an action:").assertIsDisplayed()

        // Timed options
        composeTestRule.onNodeWithText("15 MIN").assertIsDisplayed()
        composeTestRule.onNodeWithText("30 MIN").assertIsDisplayed()
        composeTestRule.onNodeWithText("1 HOUR").assertIsDisplayed()
        composeTestRule.onNodeWithText("FOREVER").assertIsDisplayed()
        composeTestRule.onNodeWithText("RETURN TO BREATHING").assertIsDisplayed()

        // 15m click
        composeTestRule.onNodeWithText("15 MIN").performClick()
        assertEquals(15L * 60L * 1000L, timedDuration)

        // 30m click
        composeTestRule.onNodeWithText("30 MIN").performClick()
        assertEquals(30L * 60L * 1000L, timedDuration)

        // 1h click
        composeTestRule.onNodeWithText("1 HOUR").performClick()
        assertEquals(60L * 60L * 1000L, timedDuration)

        // Forever click
        composeTestRule.onNodeWithText("FOREVER").performClick()
        assertTrue(foreverClicked)

        // Return click
        composeTestRule.onNodeWithText("RETURN TO BREATHING").performClick()
        assertEquals(1, dismissCount)
    }

    @Test
    fun `emergency dialog with customEmergencyMinutes shows extra badge and triggers timed duration`() {
        var timedDuration: Long? = null

        composeTestRule.setContent {
            WattimTheme {
                EmergencyDialog(
                    targetName = "TELEGRAM",
                    customEmergencyMinutes = 45,
                    onDismissRequest = {},
                    onEmergencyOnce = {},
                    onEmergencyTimed = { timedDuration = it },
                    onEmergencyForever = {}
                )
            }
        }

        // Custom badge 45 MIN must be displayed
        composeTestRule.onNodeWithText("45 MIN").assertIsDisplayed()

        // Click custom badge
        composeTestRule.onNodeWithText("45 MIN").performClick()
        assertEquals(45L * 60L * 1000L, timedDuration)
    }

    @Test
    fun `block content displays target, BLOCKED badge, until time, and exit button`() {
        var exitClicked = false
        val fixedNow = Instant.parse("2026-09-09T10:00:00Z")
        val fixedUntil = Instant.parse("2026-09-09T10:45:00Z") // 45 minutes
        val zone = ZoneId.of("UTC")

        composeTestRule.setContent {
            WattimTheme {
                BlockContent(
                    targetName = "com.bad.distraction",
                    until = fixedUntil,
                    onExit = { exitClicked = true },
                    now = fixedNow,
                    zoneId = zone
                )
            }
        }

        composeTestRule.onNodeWithText("APPLICATION BLOCKED").assertIsDisplayed()
        composeTestRule.onNodeWithText("COM.BAD.DISTRACTION").assertIsDisplayed()
        composeTestRule.onNodeWithText("00:45:00").assertIsDisplayed()
        composeTestRule.onNodeWithText("RETURN TO HOME SCREEN").assertIsDisplayed()

        composeTestRule.onNodeWithText("RETURN TO HOME SCREEN").performClick()
        assertTrue(exitClicked)
    }

    @Test
    fun `block content without until timestamp displays active block text`() {
        composeTestRule.setContent {
            WattimTheme {
                BlockContent(
                    targetName = "com.perma.blocked",
                    until = null,
                    onExit = {}
                )
            }
        }

        composeTestRule.onNodeWithText("APPLICATION BLOCKED").assertIsDisplayed()
        composeTestRule.onNodeWithText("COM.PERMA.BLOCKED").assertIsDisplayed()
        composeTestRule.onNodeWithText("00:00:00").assertIsDisplayed()
        composeTestRule.onNodeWithText("RETURN TO HOME SCREEN").assertIsDisplayed()
    }
}
