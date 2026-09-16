package io.ronesec.android.platform.accessibility

import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import io.ronesec.domain.protection.ProtectionEvent

data class RawAccessibilityPayload(
    val eventType: Int,
    val packageName: String?,
    val className: String? = null,
    val uptimeMs: Long,
    val isOverlayWindow: Boolean = false,
    val isImeWindow: Boolean = false,
    val isSubWindow: Boolean = false,
    val isSystemWindow: Boolean = false
)

/**
 * Normalizes foreground application evidence from accessibility callbacks.
 * - State-change authority: uses TYPE_WINDOW_STATE_CHANGED only.
 * - Filters IME, system UI, and own overlay windows.
 * - Recognizes own MainActivity becoming foreground as a confirmed departure.
 * - Rejects out-of-order/stale evidence via monotonic uptime watermark.
 * - Metadata-only bounded resync without reading text, hierarchy, or input.
 * Satisfies F24, Section 3.2, and Section 11.2 (T08/T09).
 */
class ForegroundTracker(
    val ownPackageName: String = "io.ronesec.android",
    val mainActivityClassName: String = "io.ronesec.android.ui.MainActivity",
    private val imePackageProvider: (() -> Set<String>)? = null,
    private val systemPackageProvider: ((String) -> Boolean)? = null,
    private val knownLauncherPackages: MutableSet<String> = mutableSetOf(
        "com.google.android.apps.nexuslauncher",
        "com.android.launcher",
        "com.android.launcher3",
        "com.sec.android.app.launcher",
        "com.miui.home",
        "com.oppo.launcher",
        "com.huawei.android.launcher",
        "com.hihonor.android.launcher"
    ),
    private val knownImePackages: MutableSet<String> = mutableSetOf(
        "com.google.android.inputmethod.latin",
        "com.android.inputmethod.latin",
        "com.touchtype.swiftkey",
        "com.samsung.android.honeyboard"
    )
) {
    private var sourceWatermarkUptimeMs: Long = 0L
    private var eventSequence: Long = 0L
    private var lastConfirmedPackage: String? = null

    companion object {
        const val ANDROID_FRAMEWORK_PACKAGE = "android"
        const val SYSTEM_UI_PACKAGE = "com.android.systemui"
    }

    fun addKnownLauncher(packageName: String) {
        knownLauncherPackages.add(packageName)
    }

    fun addKnownIme(packageName: String) {
        knownImePackages.add(packageName)
    }

    fun isLauncher(packageName: String?): Boolean {
        return packageName != null && knownLauncherPackages.contains(packageName)
    }

    fun isIme(packageName: String?): Boolean {
        if (packageName == null) return false
        if (knownImePackages.contains(packageName)) return true
        val dynamicImes = imePackageProvider?.invoke()
        if (dynamicImes != null && dynamicImes.contains(packageName)) {
            knownImePackages.add(packageName)
            return true
        }
        return false
    }

    fun isSystemUi(packageName: String?): Boolean {
        if (packageName == null) return false
        if (packageName == SYSTEM_UI_PACKAGE ||
            packageName == ANDROID_FRAMEWORK_PACKAGE ||
            packageName == "com.hihonor.systemui" ||
            packageName == "com.huawei.systemui" ||
            packageName == "com.hihonor.smartdock" ||
            packageName == "com.hihonor.sidebar" ||
            packageName == "com.hihonor.android.internal.app" ||
            packageName == "com.hihonor.floating" ||
            packageName == "com.hihonor.magicfloating" ||
            packageName == "com.hihonor.magichand" ||
            packageName == "com.hihonor.touchpanel" ||
            packageName == "com.hihonor.iaware" ||
            packageName == "com.hihonor.multiscreen" ||
            packageName == "com.hihonor.screenrecorder" ||
            packageName == "com.huawei.smartdock" ||
            packageName == "com.huawei.sidebar" ||
            packageName == "com.huawei.android.internal.app" ||
            packageName.startsWith("com.hihonor.systemui.") ||
            packageName.startsWith("com.huawei.systemui.")
        ) {
            return true
        }
        return systemPackageProvider?.invoke(packageName) == true
    }

    fun isTransientWindow(className: String?): Boolean {
        if (className.isNullOrBlank()) return false
        // Full activities must not be filtered even if they contain transient-like words
        if (className.endsWith("Activity")) return false

        return className == "android.widget.PopupWindow" ||
                className == "android.widget.ListPopupWindow" ||
                className == "android.widget.PopupMenu" ||
                className == "android.widget.Toast" ||
                className.contains("PopupWindow") ||
                className.contains("PopupDecorView") ||
                className.contains("PopupViewContainer") ||
                className.contains("Taskbar") ||
                className.contains("SmartDock") ||
                className.contains("Tooltip") ||
                className.contains("Snackbar") ||
                className.contains("FloatingToolbar") ||
                className.contains("DropDown") ||
                className.contains("Dropdown") ||
                className.endsWith("Dialog") ||
                className.endsWith("\$Dialog") ||
                className.endsWith("BottomSheet") ||
                className.endsWith("\$BottomSheet") ||
                className.endsWith("Popup") ||
                className.endsWith("Menu") ||
                className.endsWith("ContextMenu") ||
                className.endsWith("Alert") ||
                className.endsWith("ShareAlert") ||
                className.endsWith("Sheet")
    }

    fun isPopupWindow(className: String?): Boolean = isTransientWindow(className)

    fun isLauncherTaskbarNoise(className: String?): Boolean {
        if (className.isNullOrBlank()) return false
        if (className.endsWith("Activity") || className.endsWith("Launcher")) return false
        return className.contains("Taskbar") ||
                className.contains("SmartDock") ||
                className == "android.widget.FrameLayout" ||
                className == "android.view.ViewGroup" ||
                className == "android.widget.LinearLayout" ||
                className == "android.widget.RelativeLayout"
    }

    fun normalizeEvent(payload: RawAccessibilityPayload): ProtectionEvent? {
        // Only TYPE_WINDOW_STATE_CHANGED is authoritative for package transitions
        if (payload.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            return null
        }

        val pkg = payload.packageName ?: return null
        if (pkg.isBlank()) return null

        // Watermark check: ignore older evidence
        if (payload.uptimeMs < sourceWatermarkUptimeMs) {
            return null
        }
        sourceWatermarkUptimeMs = payload.uptimeMs
        val seq = ++eventSequence

        // Filter IME (by window type or package)
        if (payload.isImeWindow || isIme(pkg)) {
            return null
        }

        // Filter System UI and Android framework (by window type or package)
        if (payload.isSystemWindow || isSystemUi(pkg)) {
            return null
        }

        // Filter attached sub-windows, popups, and menus
        if (payload.isSubWindow || isTransientWindow(payload.className)) {
            return null
        }

        // Filter launcher taskbar / dock noise on tablets
        if (isLauncher(pkg) && isLauncherTaskbarNoise(payload.className)) {
            return null
        }

        // Filter own package
        if (pkg == ownPackageName) {
            if (payload.isOverlayWindow || payload.className != mainActivityClassName) {
                // Only the real MainActivity is departure evidence; own overlay windows are noise.
                return null
            }
            lastConfirmedPackage = pkg
            return ProtectionEvent.ForegroundCandidate(
                packageName = pkg,
                sourceUptimeMs = payload.uptimeMs,
                eventSequence = seq
            )
        }

        lastConfirmedPackage = pkg
        return ProtectionEvent.ForegroundCandidate(
            packageName = pkg,
            sourceUptimeMs = payload.uptimeMs,
            eventSequence = seq,
            isLauncher = isLauncher(pkg)
        )
    }

    fun extractMetadataPackage(rootNode: AccessibilityNodeInfo?): String? {
        if (rootNode == null) return null
        return try {
            val pkg = rootNode.packageName?.toString()
            if (pkg.isNullOrBlank() || isIme(pkg) || isSystemUi(pkg) || pkg == ownPackageName) {
                null
            } else {
                pkg
            }
        } finally {
            try {
                rootNode.recycle()
            } catch (_: Exception) {}
        }
    }

    fun resetWatermark(generation: Long = 0L) {
        sourceWatermarkUptimeMs = 0L
        lastConfirmedPackage = null
    }

    val currentWatermark: Long
        get() = sourceWatermarkUptimeMs

    val currentSequence: Long
        get() = eventSequence

    val lastPackage: String?
        get() = lastConfirmedPackage
}
