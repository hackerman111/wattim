package io.ronesec.android.ui

import android.os.Bundle
import io.ronesec.android.ui.designsystem.TerminalTab

sealed interface AppRoute {
    data object Onboarding : AppRoute
    data class Main(val tab: TerminalTab = TerminalTab.APPS) : AppRoute
    data class Detail(
        val packageName: String,
        val originTab: TerminalTab = TerminalTab.APPS,
        val canQuickLock: Boolean = false
    ) : AppRoute
    data class ScheduleEditor(val scheduleId: Long? = null) : AppRoute

    companion object {
        const val KEY_ROUTE_TYPE = "route_type"
        const val KEY_TAB = "route_tab"
        const val KEY_PACKAGE = "route_package"
        const val KEY_ORIGIN_TAB = "route_origin_tab"
        const val KEY_CAN_QUICK_LOCK = "route_can_quick_lock"
        const val KEY_SCHEDULE_ID = "route_schedule_id"

        const val TYPE_ONBOARDING = "onboarding"
        const val TYPE_MAIN = "main"
        const val TYPE_DETAIL = "detail"
        const val TYPE_SCHEDULE_EDITOR = "schedule_editor"

        fun toBundle(route: AppRoute): Bundle {
            return Bundle().apply {
                when (route) {
                    is Onboarding -> putString(KEY_ROUTE_TYPE, TYPE_ONBOARDING)
                    is Main -> {
                        putString(KEY_ROUTE_TYPE, TYPE_MAIN)
                        putString(KEY_TAB, route.tab.name)
                    }
                    is Detail -> {
                        putString(KEY_ROUTE_TYPE, TYPE_DETAIL)
                        putString(KEY_PACKAGE, route.packageName)
                        putString(KEY_ORIGIN_TAB, route.originTab.name)
                        putBoolean(KEY_CAN_QUICK_LOCK, route.canQuickLock)
                    }
                    is ScheduleEditor -> {
                        putString(KEY_ROUTE_TYPE, TYPE_SCHEDULE_EDITOR)
                        route.scheduleId?.let { putLong(KEY_SCHEDULE_ID, it) }
                    }
                }
            }
        }

        fun fromBundle(bundle: Bundle?): AppRoute? {
            if (bundle == null) return null
            return when (bundle.getString(KEY_ROUTE_TYPE)) {
                TYPE_ONBOARDING -> Onboarding
                TYPE_MAIN -> {
                    val tabName = bundle.getString(KEY_TAB) ?: TerminalTab.APPS.name
                    val tab = runCatching { TerminalTab.valueOf(tabName) }.getOrDefault(TerminalTab.APPS)
                    Main(tab)
                }
                TYPE_DETAIL -> {
                    val pkg = bundle.getString(KEY_PACKAGE) ?: return null
                    val originName = bundle.getString(KEY_ORIGIN_TAB) ?: TerminalTab.APPS.name
                    val origin = runCatching { TerminalTab.valueOf(originName) }.getOrDefault(TerminalTab.APPS)
                    val canQuickLock = bundle.getBoolean(KEY_CAN_QUICK_LOCK, false)
                    Detail(pkg, origin, canQuickLock)
                }
                TYPE_SCHEDULE_EDITOR -> {
                    val scheduleId = if (bundle.containsKey(KEY_SCHEDULE_ID)) bundle.getLong(KEY_SCHEDULE_ID) else null
                    ScheduleEditor(scheduleId)
                }
                else -> null
            }
        }
    }
}
