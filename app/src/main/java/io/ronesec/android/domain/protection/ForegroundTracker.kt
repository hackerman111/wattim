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
        if (eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            eventType != AccessibilityEvent.TYPE_WINDOWS_CHANGED
        ) {
            return
        }

        val rawPackage = event.packageName?.toString() ?: return

        // Ignore own app, system UI, and common system input methods
        if (rawPackage == ownPackageName ||
            rawPackage == "com.android.systemui" ||
            rawPackage.contains("inputmethod") ||
            rawPackage.contains("keyboard")
        ) {
            return
        }

        if (rawPackage == confirmedPackage) {
            return
        }

        confirmedPackage = rawPackage
        onForegroundConfirmed(rawPackage, Instant.now())
    }

    fun reset() {
        confirmedPackage = null
    }

    fun currentConfirmedPackage(): String? = confirmedPackage
}
