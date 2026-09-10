package io.ronesec.android.ui.designsystem

import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import io.ronesec.android.ui.MainActivity
import io.ronesec.android.ui.designsystem.gallery.ComponentGallery
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TwoComposeRootsTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun `common theme root works in two independent compose scopes without state leakage`() {
        var root1Color: Color? = null
        var root2Color: Color? = null

        composeTestRule.setContent {
            // Simulated Root 1 (e.g. Activity Window)
            WattimTheme(themeId = ThemeId.CYBER_TERMINAL) {
                root1Color = WattimTheme.colors.accent
                Text("Root 1 Content")
            }

            // Simulated Root 2 (e.g. WindowManager Overlay Window)
            WattimTheme(themeId = ThemeId.DRACULA) {
                root2Color = WattimTheme.colors.accent
                Text("Root 2 Content")
            }
        }

        assertNotNull(root1Color)
        assertNotNull(root2Color)
        assertEquals(ThemeRegistry.CyberTerminal.accent, root1Color)
        assertEquals(ThemeRegistry.Dracula.accent, root2Color)
    }

    @Test
    fun `component gallery renders cleanly in previewable test fixture`() {
        composeTestRule.setContent {
            ComponentGallery(selectedTheme = ThemeId.NORD)
        }
    }
}
