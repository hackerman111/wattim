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
        composeRule.onNodeWithText("Emergency access code: 1234567890").assertIsDisplayed()
    }

    @Test
    fun `timed emergency submits exactly the manually entered ten digits`() {
        var submittedDuration = 0L
        var submittedCode = ""
        var once = false
        composeRule.setContent {
            WattimTheme {
                EmergencyDialog(
                    targetName = "Target",
                    onDismissRequest = {},
                    onEmergencyOnce = { once = true },
                    onEmergencyTimed = {},
                    onEmergencyForever = {},
                    requireCode = true,
                    onTimedWithCode = { duration, code ->
                        submittedDuration = duration
                        submittedCode = code
                    }
                )
            }
        }

        composeRule.onNodeWithContentDescription("Enter the 10-digit code from the intervention screen")
            .performTextInput("12345678909")
        composeRule.onNodeWithText("15 MIN").performClick()
        assertEquals(15L * 60L * 1000L, submittedDuration)
        assertEquals("1234567890", submittedCode)

        composeRule.onNodeWithText("ENTER ONCE").performClick()
        assertTrue(once)
    }
}
