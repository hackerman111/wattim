package io.ronesec.android.platform.accessibility

import android.accessibilityservice.AccessibilityServiceInfo

interface AccessibilityServiceConfigAdapter {
    fun getServiceInfo(): AccessibilityServiceInfo?
    fun setServiceInfo(info: AccessibilityServiceInfo)
}

data class SubscriptionConfig(
    val targetPackages: Set<String>,
    val trackAll: Boolean
)

/**
 * Adaptive Accessibility service subscription controller.
 * - Idle: Subscribes only to configured enabled target packages with notificationTimeout=25ms.
 * - Active target / intervening / granted / exiting: Widens subscription to all packages (packageNames=null).
 * - Zero enabled targets: Subscribes to a dummy non-existent package to avoid null (accidental all-packages subscription).
 * - Avoids duplicate serviceInfo IPC if desired config matches applied config.
 * Satisfies F25, Section 6, and Section 11.2 (T09).
 */
class SubscriptionController(
    private val adapter: AccessibilityServiceConfigAdapter? = null
) {
    private var appliedConfig: SubscriptionConfig? = null

    companion object {
        const val NOTIFICATION_TIMEOUT_MS = 25L
        const val DUMMY_NO_TARGETS_PACKAGE = "io.ronesec.android.dummy_no_targets"
    }

    fun applyConfiguration(targetPackages: Set<String>, trackAll: Boolean): Boolean {
        val desired = SubscriptionConfig(targetPackages, trackAll)
        if (desired == appliedConfig) {
            return false // Already applied, skip IPC
        }

        val serviceAdapter = adapter ?: return false
        val info = serviceAdapter.getServiceInfo() ?: AccessibilityServiceInfo()

        info.notificationTimeout = NOTIFICATION_TIMEOUT_MS

        if (trackAll) {
            // null packageNames in AccessibilityServiceInfo means listen to all packages
            info.packageNames = null
        } else {
            if (targetPackages.isEmpty()) {
                // Zero enabled targets: must not set null, which would mean all packages!
                info.packageNames = arrayOf(DUMMY_NO_TARGETS_PACKAGE)
            } else {
                info.packageNames = targetPackages.toTypedArray()
            }
        }

        serviceAdapter.setServiceInfo(info)
        appliedConfig = desired
        return true
    }

    val currentAppliedConfig: SubscriptionConfig?
        get() = appliedConfig

    fun reset() {
        appliedConfig = null
    }
}
