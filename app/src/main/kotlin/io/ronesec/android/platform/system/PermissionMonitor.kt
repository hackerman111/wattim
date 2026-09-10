package io.ronesec.android.platform.system

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.ronesec.android.platform.accessibility.AppMonitorService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class PermissionState {
    Granted,
    Denied
}

enum class FgsStatus {
    Stopped,
    Starting,
    Running,
    StartFailed
}

enum class OnboardingStep(val stepNumber: String) {
    Accessibility("01/03"),
    Overlay("02/03"),
    BatteryExemption("03/03")
}

data class PermissionSnapshot(
    val accessibility: PermissionState = PermissionState.Denied,
    val isAccessibilityConnected: Boolean = false,
    val mediaControl: PermissionState = PermissionState.Denied,
    val overlay: PermissionState = PermissionState.Denied,
    val batteryExemption: PermissionState = PermissionState.Denied,
    val notifications: PermissionState = PermissionState.Denied,
    val fgsStatus: FgsStatus = FgsStatus.Stopped,
    val isProtectionOperational: Boolean = false
) {
    /**
     * All three core permissions required for basic protection.
     * Notifications denial on API 33+ does not block protection.
     */
    val areRequiredPermissionsGranted: Boolean
        get() = accessibility == PermissionState.Granted &&
                overlay == PermissionState.Granted &&
                batteryExemption == PermissionState.Granted

    val firstMissingStep: OnboardingStep?
        get() = when {
            accessibility != PermissionState.Granted -> OnboardingStep.Accessibility
            overlay != PermissionState.Granted -> OnboardingStep.Overlay
            batteryExemption != PermissionState.Granted -> OnboardingStep.BatteryExemption
            else -> null
        }

    val isProtectionActive: Boolean
        get() = areRequiredPermissionsGranted &&
                isAccessibilityConnected &&
                isProtectionOperational &&
                fgsStatus == FgsStatus.Running
}

interface PlatformPermissionChecker {
    fun isAccessibilityEnabled(): Boolean
    fun isMediaControlEnabled(): Boolean
    fun canDrawOverlays(): Boolean
    fun isIgnoringBatteryOptimizations(): Boolean
    fun areNotificationsEnabled(): Boolean
}

class AndroidPlatformPermissionChecker(
    private val context: Context
) : PlatformPermissionChecker {

    override fun isAccessibilityEnabled(): Boolean {
        val targetService = ComponentName(context, AppMonitorService::class.java)
        val targetFlat = targetService.flattenToString()
        val targetShort = targetService.flattenToShortString()

        // 1. Check Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
        if (!enabledServices.isNullOrEmpty()) {
            val isFound = enabledServices.split(':').any {
                it.equals(targetFlat, ignoreCase = true) || it.equals(targetShort, ignoreCase = true)
            }
            if (isFound) return true
        }

        // 2. Check AccessibilityManager list
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
        if (am != null) {
            val services = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            val isFound = services.any { serviceInfo ->
                serviceInfo.resolveInfo?.serviceInfo?.let { si ->
                    si.packageName == targetService.packageName && si.name == targetService.className
                } ?: false
            }
            if (isFound) return true
        }

        return false
    }

    override fun canDrawOverlays(): Boolean {
        return Settings.canDrawOverlays(context)
    }

    override fun isMediaControlEnabled(): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    override fun isIgnoringBatteryOptimizations(): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        return powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: false
    }

    override fun areNotificationsEnabled(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
    }
}

class PermissionMonitor(
    private val checker: PlatformPermissionChecker
) {
    private val _statusFlow = MutableStateFlow(PermissionSnapshot())
    val statusFlow: StateFlow<PermissionSnapshot> = _statusFlow.asStateFlow()

    init {
        refresh()
    }

    fun checkAll(): PermissionSnapshot {
        val accessibilityState = if (checker.isAccessibilityEnabled()) {
            PermissionState.Granted
        } else {
            PermissionState.Denied
        }

        val overlayState = if (checker.canDrawOverlays()) {
            PermissionState.Granted
        } else {
            PermissionState.Denied
        }

        val mediaControlState = if (checker.isMediaControlEnabled()) {
            PermissionState.Granted
        } else {
            PermissionState.Denied
        }

        val batteryState = if (checker.isIgnoringBatteryOptimizations()) {
            PermissionState.Granted
        } else {
            PermissionState.Denied
        }

        val notificationState = if (checker.areNotificationsEnabled()) {
            PermissionState.Granted
        } else {
            PermissionState.Denied
        }

        val updated = _statusFlow.value.copy(
            accessibility = accessibilityState,
            mediaControl = mediaControlState,
            isAccessibilityConnected = if (accessibilityState == PermissionState.Granted) {
                _statusFlow.value.isAccessibilityConnected
            } else {
                false
            },
            overlay = overlayState,
            batteryExemption = batteryState,
            notifications = notificationState
        )
        _statusFlow.value = updated
        return updated
    }

    fun refresh(): PermissionSnapshot = checkAll()

    fun setAccessibilityConnected(connected: Boolean) {
        _statusFlow.update { it.copy(isAccessibilityConnected = connected) }
    }

    fun setProtectionOperational(operational: Boolean) {
        _statusFlow.update { it.copy(isProtectionOperational = operational) }
    }

    fun setFgsStatus(status: FgsStatus) {
        _statusFlow.update { it.copy(fgsStatus = status) }
    }
}
