package io.ronesec.android.domain.protection

import android.view.accessibility.AccessibilityEvent
import java.time.Instant

class ForegroundTracker(
    private val ownPackageName: String,
    private val isAdditionalIgnoredPackage: (String) -> Boolean = { false },
    private val onForegroundConfirmed: (packageName: String, timestamp: Instant) -> Unit
) {
    private var confirmedPackage: String? = null

    fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val rawPackage = event.packageName?.toString()

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                if (rawPackage.isNullOrEmpty()) return
                if (isSystemOrIgnored(rawPackage)) return
                if (rawPackage == confirmedPackage) return

                confirmedPackage = rawPackage
                onForegroundConfirmed(rawPackage, Instant.now())
            }

            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> {
                // Window-hierarchy changes are not foreground transitions.
                // In particular, removing our overlay during GLOBAL_ACTION_HOME can produce
                // a stale TYPE_WINDOWS_CHANGED event for the previously foreground target.
                // Promoting that event to foreground after the launcher was already confirmed
                // creates a fresh target session and re-opens the intervention overlay.
                //
                // TYPE_WINDOW_STATE_CHANGED remains the authoritative foreground signal.
                return
            }
        }
    }

    private fun isSystemOrIgnored(pkg: String): Boolean {
        return pkg == ownPackageName ||
                pkg == "com.android.systemui" ||
                pkg == "android" ||
                pkg.contains("inputmethod") ||
                pkg.contains("keyboard") ||
                isAdditionalIgnoredPackage(pkg)
    }

    fun reset() {
        confirmedPackage = null
    }

    fun currentConfirmedPackage(): String? = confirmedPackage
}
