package io.ronesec.android.ui.i18n

import androidx.compose.runtime.staticCompositionLocalOf
import io.ronesec.android.domain.model.AnimationType
import java.time.DayOfWeek

enum class AppLanguage(val code: String, val displayName: String) {
    SYSTEM("system", "Авто / System"),
    EN("en", "English"),
    RU("ru", "Русский");

    companion object {
        fun fromCode(code: String?): AppLanguage =
            entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: SYSTEM
    }
}

interface AppStrings {
    // Navigation
    val navFocus: String
    val navBlock: String
    val navStats: String
    val navConfig: String

    // Home
    val protectedSection: String
    val addAppButton: String
    val pauseProtectionTitle: String
    val pause15m: String
    val pause30m: String
    val pause1h: String
    val pauseForever: String
    val protectionPausedTitle: String
    val remainingPrefix: String
    val resumeButton: String
    val todayHeader: String
    val savedUnit: String
    val victoriesUnit: String
    val attemptsUnit: String
    val noProtectedApps: String
    val noProtectedAppsSubtitle: String

    // Add App Dialog
    val selectAppTitle: String
    val searchAppsPlaceholder: String
    val addAppItemButton: String
    val cancelButton: String
    val noAppsAvailable: String

    // Config / Settings
    val settingsTitle: String
    val languageSection: String
    val themeSection: String
    val activeBadge: String
    val sessionDurationSection: String
    val sessionDurationDesc: String
    val minutesUnit: String
    val secondsShort: String
    val secondsLabel: String
    val secondsUnitShort: String
    val minutesShort: String
    val hoursShort: String
    val statsOnOverlaySection: String
    val statsOnOverlayDesc: String
    val onLabel: String
    val offLabel: String
    val systemPermissionsSection: String
    val permAccessibility: String
    val permAccessibilityDesc: String
    val permOverlay: String
    val permOverlayDesc: String
    val permBattery: String
    val permBatteryDesc: String
    val readyBadge: String
    val enableButton: String
    val googleWarningTitle: String
    val googleWarningDesc: String
    val openAppSettingsButton: String
    val privacySection: String
    val privacyBullets: String

    // Onboarding
    val onboardingTitle: String
    val systemReadyTitle: String
    val stepAccessibilityTitle: String
    val stepAccessibilityDesc: String
    val stepOverlayTitle: String
    val stepOverlayDesc: String
    val stepBatteryTitle: String
    val stepBatteryDesc: String
    val openAccessibilityButton: String
    val allowOverlayButton: String
    val disableBatteryOptButton: String
    val startWattimButton: String
    val checkStatusButton: String

    // Intervention Overlay
    val phaseInhale: String
    val phaseHold: String
    val phaseExhale: String
    val phaseRest: String
    val exitButton: String
    val continueButton: String
    val emergencyButton: String
    val emergencyDialogTitle: String
    val emergencyDialogDesc: String
    val emergencyEnterOnce: String
    val emergencyPauseApp: String
    val emergencyResumeBreath: String
    fun savedTimeOverlay(time: String): String

    // Animation Names
    fun animationName(type: AnimationType): String

    // Stats
    val statsTitle: String
    val lifeTimeSavedCardTitle: String
    fun totalImpulsiveAvoided(count: Int): String
    val todaySaved: String
    val todaySummaryTitle: String
    val metricOpenAttempts: String
    val metricContinued: String
    val metricClosed: String
    val metricMindfulness: String
    val perAppTitle: String
    val tableHeaderApp: String
    val tableHeaderOpen: String
    val tableHeaderClosed: String
    val noActivityToday: String

    // Blocks
    val blocksTitle: String
    val focusSessionActive: String
    fun blockedAppsCount(count: Int): String
    val stopSessionButton: String
    val quickFocusSession: String
    fun forAppsCount(selected: Int, total: Int): String
    val deselectAll: String
    val selectAll: String
    val defaultFocusSessionName: String
    val blockSchedulesTitle: String
    val createScheduleButton: String
    val noSchedulesTitle: String
    val noSchedulesSubtitle: String
    val editButton: String
    val deleteButton: String
    val fullBlockBadge: String
    val scheduledInterventionsBadge: String
    fun allAppsBadge(count: Int): String
    val noneSelectedLabel: String
    val blockedPrefix: String
    val interventionsPrefix: String
    val editScheduleTitle: String
    val newScheduleTitle: String
    val scheduleNameLabel: String
    val scheduleTypeLabel: String
    val hardBlockTypeTitle: String
    val hardBlockTypeDesc: String
    val customInterventionsTitle: String
    val customInterventionsDesc: String
    val selectedBadge: String
    val timeWindowPrefix: String
    val presetWork: String
    val presetNight: String
    val presetMorning: String
    val presetEvening: String
    val startLabel: String
    val endLabel: String
    val hoursLabel: String
    val minutesLabel: String
    val daysOfWeekTitle: String
    val weekdaysBadge: String
    val weekendsBadge: String
    val allDaysBadge: String
    fun dayShortName(day: DayOfWeek): String
    fun dayChipName(day: DayOfWeek): String
    val noProtectedAppsBlocksHint: String
    val saveButton: String
    val defaultScheduleNameBlock: String
    val defaultScheduleNameIntervention: String
    fun customRulesTitle(appName: String): String
    val pauseDurationTitle: String
    val repeatInterventionTitle: String
    val optionOff: String

    // Target Settings
    val previewSavedTime: String
    val targetProtectionStatus: String
    val targetStatusActive: String
    val targetStatusDisabled: String
    val targetMindfulnessPhrase: String
    val targetDefaultPhrase: String
    val targetAnimationType: String
    val targetPauseDuration: String
    val targetPreviewAnimation: String
    val targetReintercept: String
    val targetCustomOption: String
    val targetCustomInterval: String
    val targetQuickPresets: String
    val targetReinterceptOffDesc: String
    fun targetReinterceptOnDesc(timeStr: String): String
    val targetQuickReturn: String
    val targetQuickReturn0s: String
    fun targetQuickReturnGrace(sec: Long): String
    val targetExponentialGrowth: String
    val targetExponentialGrowthDesc: String
    val targetGrowthPercent: String
    val targetRollingWindow: String
    fun targetProjectionTitle(growthPercent: Int): String
    fun targetOpenNumber(k: Int): String
    fun targetProjectionBaseHint(baseSec: Int): String
    val targetQuickLock: String
    fun targetQuickLockDesc(appName: String): String
    fun targetQuickLockFocusPrefix(appName: String): String
    val saveChangesButton: String
    val removeFromProtectionButton: String

    // Overlay
    val blockOverlayTitle: String
    val blockOverlayClose: String
    fun reinterventionPrompt(appName: String, timeStr: String): String

    // Format helper
    fun formatSavedTime(minutes: Long): String
    fun formatDuration(totalMs: Long): String
}

fun resolveAppStrings(language: AppLanguage): AppStrings = when (language) {
    AppLanguage.SYSTEM -> {
        val sysLocale = java.util.Locale.getDefault().language
        if (sysLocale.lowercase().startsWith("ru")) RuStrings else EnStrings
    }
    AppLanguage.EN -> EnStrings
    AppLanguage.RU -> RuStrings
}

val LocalAppStrings = staticCompositionLocalOf<AppStrings> { EnStrings }
