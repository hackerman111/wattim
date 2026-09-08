package io.ronesec.android.domain.protection

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityEvent

class AccessibilitySubscriptionController(
    private val service: AccessibilityService
) {
    companion object {
        /**
         * Notification throttling in milliseconds to prevent excessive IPC wakeups
         * while keeping target app interception latency well under 50ms p95.
         */
        const val NOTIFICATION_TIMEOUT_MS = 25L
    }

    private var currentTargetActive: Boolean = false
    private var currentTargets: Set<String> = emptySet()

    fun updateSubscription(targetPackages: Set<String>, isTargetActive: Boolean) {
        currentTargets = targetPackages
        currentTargetActive = isTargetActive

        try {
            val info = service.serviceInfo ?: return
            info.notificationTimeout = NOTIFICATION_TIMEOUT_MS

            if (isTargetActive || targetPackages.isEmpty()) {
                // When a target app is active, subscribe to all packages (packageNames = null)
                // to detect exits to launcher or another app immediately.
                info.packageNames = null
                info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                        AccessibilityEvent.TYPE_WINDOWS_CHANGED
            } else {
                // When Idle, restrict subscription only to enabled target packages
                // to eliminate background wakeups and IPC overhead during non-target usage.
                info.packageNames = targetPackages.toTypedArray()
                info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            }
            service.serviceInfo = info
        } catch (_: Exception) {}
    }
}
