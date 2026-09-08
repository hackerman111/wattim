package io.ronesec.android.domain.protection

import android.view.accessibility.AccessibilityEvent
import java.time.Instant

class ForegroundTracker(
    private val ownPackageName: String,
    private val onForegroundConfirmed: (packageName: String, timestamp: Instant) -> Unit
) {
    private var confirmedPackage: String? = null

    fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val eventType = event.eventType
        val rawPackage = event.packageName?.toString()

        // TYPE_WINDOW_STATE_CHANGED: primary fast signal for activity / window transitions
        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            if (rawPackage.isNullOrEmpty()) return
            if (isSystemOrIgnored(rawPackage)) return
            if (rawPackage == confirmedPackage) return

            confirmedPackage = rawPackage
            onForegroundConfirmed(rawPackage, Instant.now())
            return
        }

        // TYPE_WINDOWS_CHANGED: window hierarchy changed
        // Never treat rawPackage on TYPE_WINDOWS_CHANGED as an unconditional foreground app!
        // Only accept if package is non-null, valid, and not ignored.
        if (eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            if (rawPackage.isNullOrEmpty()) return
            if (isSystemOrIgnored(rawPackage)) return
            if (rawPackage != confirmedPackage) {
                confirmedPackage = rawPackage
                onForegroundConfirmed(rawPackage, Instant.now())
            }
        }
    }

    private fun isSystemOrIgnored(pkg: String): Boolean {
        return pkg == ownPackageName ||
                pkg == "com.android.systemui" ||
                pkg == "android" ||
                pkg.contains("inputmethod") ||
                pkg.contains("keyboard")
    }

    fun reset() {
        confirmedPackage = null
    }

    fun currentConfirmedPackage(): String? = confirmedPackage
}
