package io.ronesec.android.ui

import androidx.compose.ui.unit.dp
import io.ronesec.android.ui.designsystem.WattimDimensions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class T22_OnboardingAccessibilityAndSemanticsTest {

    @Test
    fun minimumTouchTargetIsAtLeast48dp() {
        val dimensions = WattimDimensions()
        assertTrue("Min touch target must be at least 48dp", dimensions.minTouchTarget >= 48.dp)
        assertEquals(48.dp, dimensions.minTouchTarget)
    }

    @Test
    fun onboardingAndNavigationStringsExistInBothLocales() {
        val stringsEn = findStringsFile("src/main/res/values/strings.xml")
        val stringsRu = findStringsFile("src/main/res/values-ru/strings.xml")

        assertTrue("English strings file must exist", stringsEn.exists())
        assertTrue("Russian strings file must exist", stringsRu.exists())

        val enContent = stringsEn.readText()
        val ruContent = stringsRu.readText()

        val requiredStringKeys = listOf(
            "app_name",
            "nav_apps",
            "nav_block",
            "nav_stats",
            "nav_config",
            "action_enable",
            "action_open_accessibility",
            "action_allow_overlay",
            "action_disable_battery_optimization",
            "action_start_wattim",
            "action_check_status",
            "action_continue",
            "action_return",
            "onboarding_step1_title",
            "onboarding_step1_desc",
            "onboarding_step2_title",
            "onboarding_step2_desc",
            "onboarding_step3_title",
            "onboarding_step3_desc",
            "restricted_settings_title",
            "restricted_settings_desc",
            "action_open_app_info",
            "onboarding_ready_title",
            "onboarding_ready_desc",
            "badge_accessibility",
            "badge_overlay",
            "badge_battery",
            "badge_ready",
            "fgs_status_active",
            "fgs_status_degraded"
        )

        for (key in requiredStringKeys) {
            val needle = "name=\"$key\""
            assertTrue("English strings must define '$key'", enContent.contains(needle))
            assertTrue("Russian strings must define '$key'", ruContent.contains(needle))
        }
    }

    private fun findStringsFile(relativePath: String): File {
        val direct = File(relativePath)
        if (direct.exists()) return direct
        val fromApp = File("app", relativePath)
        if (fromApp.exists()) return fromApp
        val fromRoot = File("../app", relativePath)
        if (fromRoot.exists()) return fromRoot
        return direct
    }
}
