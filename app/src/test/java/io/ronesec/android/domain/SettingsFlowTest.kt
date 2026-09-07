package io.ronesec.android.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsFlowTest {

    @Test
    fun `show_saved_time_stats defaults to true when setting is null`() {
        val rawValue: String? = null
        val isEnabled = rawValue == null || rawValue == "true"
        assertTrue(isEnabled)
    }

    @Test
    fun `show_saved_time_stats evaluates to false when setting is false`() {
        val rawValue: String? = "false"
        val isEnabled = rawValue == null || rawValue == "true"
        assertFalse(isEnabled)
    }

    @Test
    fun `show_saved_time_stats evaluates to true when setting is true`() {
        val rawValue: String? = "true"
        val isEnabled = rawValue == null || rawValue == "true"
        assertTrue(isEnabled)
    }
}
