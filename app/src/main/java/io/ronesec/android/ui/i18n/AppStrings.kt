package io.ronesec.android.ui.i18n

import androidx.compose.runtime.staticCompositionLocalOf

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
    fun animationName(type: io.ronesec.android.domain.model.AnimationType): String

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
    fun dayShortName(day: java.time.DayOfWeek): String
    fun dayChipName(day: java.time.DayOfWeek): String
    val noProtectedAppsBlocksHint: String
    val saveButton: String
    val defaultScheduleNameBlock: String
    val defaultScheduleNameIntervention: String
    fun customRulesTitle(appName: String): String
    val pauseDurationTitle: String
    val secondsShort: String
    val secondsLabel: String
    val secondsUnitShort: String
    val minutesShort: String
    val hoursShort: String
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

object EnStrings : AppStrings {
    override val navFocus = "FOCUS"
    override val navBlock = "BLOCKS"
    override val navStats = "STATS"
    override val navConfig = "SETTINGS"

    override val protectedSection = "PROTECTED APPS"
    override val addAppButton = "+ ADD APP"
    override val pauseProtectionTitle = "TEMPORARILY PAUSE PROTECTION"
    override val pause15m = "15 MIN"
    override val pause30m = "30 MIN"
    override val pause1h = "1 HOUR"
    override val pauseForever = "INDEFINITELY"
    override val protectionPausedTitle = "PROTECTION PAUSED"
    override val remainingPrefix = "Remaining:"
    override val resumeButton = "RESUME"
    override val todayHeader = "TODAY"
    override val savedUnit = "saved"
    override val victoriesUnit = "victories"
    override val attemptsUnit = "attempts"
    override val noProtectedApps = "NO PROTECTED APPS"
    override val noProtectedAppsSubtitle = "Add apps that distract you throughout the day"

    override val selectAppTitle = "SELECT APPLICATION"
    override val searchAppsPlaceholder = "Search by name..."
    override val addAppItemButton = "ADD"
    override val cancelButton = "CANCEL"
    override val noAppsAvailable = "NO APPS AVAILABLE"

    override val settingsTitle = "SETTINGS"
    override val languageSection = "INTERFACE LANGUAGE"
    override val themeSection = "COLOR THEME"
    override val activeBadge = "ACTIVE"
    override val sessionDurationSection = "AVERAGE SESSION DURATION"
    override val sessionDurationDesc = "Used to estimate time saved when you close target apps"
    override val minutesUnit = "min"
    override val secondsShort = "s"
    override val secondsLabel = "SECONDS"
    override val secondsUnitShort = "s"
    override val minutesShort = "m"
    override val hoursShort = "h"
    override val statsOnOverlaySection = "STATS ON PAUSE SCREEN"
    override val statsOnOverlayDesc = "Show \"You already saved...\" line over the mindfulness animation"
    override val onLabel = "ON"
    override val offLabel = "OFF"
    override val systemPermissionsSection = "SYSTEM PERMISSIONS"
    override val permAccessibility = "ACCESSIBILITY SERVICE"
    override val permAccessibilityDesc = "Detect protected app launches in real-time"
    override val permOverlay = "DISPLAY OVER OTHER APPS"
    override val permOverlayDesc = "Full-screen mindful breathing overlay"
    override val permBattery = "BACKGROUND WORK"
    override val permBatteryDesc = "Prevent system from killing the service"
    override val readyBadge = "READY"
    override val enableButton = "ENABLE"
    override val googleWarningTitle = "SETTINGS BLOCKED BY SYSTEM OR GOOGLE?"
    override val googleWarningDesc = "Google Play Protect or the system may warn about a suspicious app due to overlay mechanics. wattim is 100% offline and requests zero network access.\n\nIf accessibility settings are blocked (\"Restricted setting\" on Android 13+):\n1. Open wattim app settings in system (button below)\n2. Tap the three dots (⋮) in the top-right corner\n3. Select \"Allow restricted settings\""
    override val openAppSettingsButton = "OPEN WATTIM SETTINGS IN ANDROID"
    override val privacySection = "SECURITY AND PRIVACY"
    override val privacyBullets = "• 100% OFFLINE (ZERO NETWORK ACCESS)\n• ZERO TELEMETRY OR TRACKING\n• ALL DATA STORED IN LOCAL ROOM DATABASE"

    override val onboardingTitle = "WATTIM SETUP"
    override val systemReadyTitle = "SYSTEM READY"
    override val stepAccessibilityTitle = "ACCESSIBILITY SERVICE"
    override val stepAccessibilityDesc = "Required to detect launches of protected apps in real time."
    override val stepOverlayTitle = "DISPLAY OVER OTHER APPS"
    override val stepOverlayDesc = "Required to display the full-screen mindfulness overlay before app opens."
    override val stepBatteryTitle = "BACKGROUND WORK (BATTERY)"
    override val stepBatteryDesc = "Disable battery optimization for wattim so Android keeps the protection service alive."
    override val openAccessibilityButton = "OPEN ACCESSIBILITY"
    override val allowOverlayButton = "ALLOW OVERLAY"
    override val disableBatteryOptButton = "DISABLE BATTERY OPTIMIZATION"
    override val startWattimButton = "START USING WATTIM"
    override val checkStatusButton = "CHECK STATUS"

    override val phaseInhale = "Inhale"
    override val phaseHold = "Hold"
    override val phaseExhale = "Exhale"
    override val phaseRest = "Rest"
    override val exitButton = "EXIT"
    override val continueButton = "CONTINUE"
    override val emergencyButton = "EMERGENCY ACCESS"
    override val emergencyDialogTitle = "Are you sure?"
    override val emergencyDialogDesc = "Are you sure you want to bypass the pause? Pick an option:"
    override val emergencyEnterOnce = "Enter once"
    override val emergencyPauseApp = "Pause protection for this app"
    override val emergencyResumeBreath = "Resume breathing"
    override fun savedTimeOverlay(time: String) = "🌱 You have already saved $time of your life"

    override fun animationName(type: io.ronesec.android.domain.model.AnimationType): String = when (type) {
        io.ronesec.android.domain.model.AnimationType.FILL -> "Fill"
        io.ronesec.android.domain.model.AnimationType.PULSE -> "Breathing Sphere"
        io.ronesec.android.domain.model.AnimationType.CIRCLE -> "Zen Vortex"
        io.ronesec.android.domain.model.AnimationType.HORIZONTAL_SWEEP -> "Horizontal Wave"
        io.ronesec.android.domain.model.AnimationType.VERTICAL_SWEEP -> "Vertical Wave"
    }

    override val statsTitle = "STATISTICS"
    override val lifeTimeSavedCardTitle = "TIME OF LIFE SAVED"
    override fun totalImpulsiveAvoided(count: Int) = "Total impulsive sessions avoided: $count"
    override val todaySaved = "Today saved:"
    override val todaySummaryTitle = "TODAY'S SUMMARY"
    override val metricOpenAttempts = "OPEN ATTEMPTS"
    override val metricContinued = "CONTINUED"
    override val metricClosed = "CLOSED"
    override val metricMindfulness = "MINDFULNESS"
    override val perAppTitle = "BY APP"
    override val tableHeaderApp = "APP"
    override val tableHeaderOpen = "OPEN"
    override val tableHeaderClosed = "CLOSED"
    override val noActivityToday = "NO ACTIVITY RECORDED TODAY"

    override val blocksTitle = "BLOCKING"
    override val focusSessionActive = "FOCUS SESSION ACTIVE"
    override fun blockedAppsCount(count: Int) = "Blocked apps: $count"
    override val stopSessionButton = "STOP SESSION"
    override val quickFocusSession = "QUICK FOCUS SESSION"
    override fun forAppsCount(selected: Int, total: Int) = "FOR APPS ($selected/$total)"
    override val deselectAll = "Deselect all"
    override val selectAll = "Select all"
    override val defaultFocusSessionName = "Focus session"
    override val blockSchedulesTitle = "BLOCK SCHEDULES"
    override val createScheduleButton = "+ CREATE SCHEDULE"
    override val noSchedulesTitle = "NO SCHEDULES"
    override val noSchedulesSubtitle = "Create a schedule to automatically block apps during set hours."
    override val editButton = "EDIT"
    override val deleteButton = "DELETE"
    override val fullBlockBadge = "🔒 Full block"
    override val scheduledInterventionsBadge = "🧘 Scheduled interventions"
    override fun allAppsBadge(count: Int) = "All apps ($count)"
    override val noneSelectedLabel = "None selected"
    override val blockedPrefix = "Blocked"
    override val interventionsPrefix = "Interventions"
    override val editScheduleTitle = "EDIT SCHEDULE"
    override val newScheduleTitle = "NEW SCHEDULE"
    override val scheduleNameLabel = "SCHEDULE NAME"
    override val scheduleTypeLabel = "SCHEDULE TYPE"
    override val hardBlockTypeTitle = "HARD BLOCK"
    override val hardBlockTypeDesc = "Apps are completely blocked during specified hours."
    override val customInterventionsTitle = "CUSTOM INTERVENTIONS"
    override val customInterventionsDesc = "Apps open via mindfulness pause with custom limits."
    override val selectedBadge = "SELECTED"
    override val timeWindowPrefix = "WINDOW"
    override val presetWork = "Work (9-18)"
    override val presetNight = "Night (23-7)"
    override val presetMorning = "Morning (7-12)"
    override val presetEvening = "Evening (18-23)"
    override val startLabel = "START"
    override val endLabel = "END"
    override val hoursLabel = "HOURS"
    override val minutesLabel = "MINUTES"
    override val daysOfWeekTitle = "DAYS OF WEEK"
    override val weekdaysBadge = "WEEKDAYS"
    override val weekendsBadge = "WEEKENDS"
    override val allDaysBadge = "EVERY DAY"
    override fun dayShortName(day: java.time.DayOfWeek): String = when (day) {
        java.time.DayOfWeek.MONDAY -> "Mon"
        java.time.DayOfWeek.TUESDAY -> "Tue"
        java.time.DayOfWeek.WEDNESDAY -> "Wed"
        java.time.DayOfWeek.THURSDAY -> "Thu"
        java.time.DayOfWeek.FRIDAY -> "Fri"
        java.time.DayOfWeek.SATURDAY -> "Sat"
        java.time.DayOfWeek.SUNDAY -> "Sun"
    }
    override fun dayChipName(day: java.time.DayOfWeek): String = when (day) {
        java.time.DayOfWeek.MONDAY -> "MON"
        java.time.DayOfWeek.TUESDAY -> "TUE"
        java.time.DayOfWeek.WEDNESDAY -> "WED"
        java.time.DayOfWeek.THURSDAY -> "THU"
        java.time.DayOfWeek.FRIDAY -> "FRI"
        java.time.DayOfWeek.SATURDAY -> "SAT"
        java.time.DayOfWeek.SUNDAY -> "SUN"
    }
    override val noProtectedAppsBlocksHint = "No protected apps. Add them in the Focus tab."
    override val saveButton = "SAVE"
    override val defaultScheduleNameBlock = "Block"
    override val defaultScheduleNameIntervention = "Interventions"
    override fun customRulesTitle(appName: String) = "Custom rules: $appName"
    override val pauseDurationTitle = "PAUSE DURATION"
    override val repeatInterventionTitle = "REPEAT INTERVENTION"
    override val optionOff = "Off"

    override val previewSavedTime = "Preview: you have saved 2 days of life"
    override val targetProtectionStatus = "PROTECTION STATUS"
    override val targetStatusActive = "ACTIVE"
    override val targetStatusDisabled = "DISABLED"
    override val targetMindfulnessPhrase = "MINDFULNESS PHRASE"
    override val targetDefaultPhrase = "Take a deep breath"
    override val targetAnimationType = "ANIMATION TYPE"
    override val targetPauseDuration = "PAUSE DURATION (INHALE & EXHALE)"
    override val targetPreviewAnimation = "PREVIEW ANIMATION"
    override val targetReintercept = "RE-INTERCEPT"
    override val targetCustomOption = "CUSTOM"
    override val targetCustomInterval = "CUSTOM INTERVAL"
    override val targetQuickPresets = "QUICK PRESETS"
    override val targetReinterceptOffDesc = "Re-intercept during continuous app usage is disabled."
    override fun targetReinterceptOnDesc(timeStr: String) = "After $timeStr of continuous use, a prompt asking \"Continue?\" will appear."
    override val targetQuickReturn = "QUICK RETURN (GRACE PERIOD)"
    override val targetQuickReturn0s = "0s — new animation will trigger immediately upon closing and re-opening."
    override fun targetQuickReturnGrace(sec: Long) = "If you return to the app within $sec sec, the pause will not be shown."
    override val targetExponentialGrowth = "EXPONENTIAL DELAY"
    override val targetExponentialGrowthDesc = "Increases pause duration on frequent repeated launches within rolling window."
    override val targetGrowthPercent = "GROWTH PERCENT PER OPEN"
    override val targetRollingWindow = "ROLLING WINDOW"
    override fun targetProjectionTitle(growthPercent: Int) = "DELAY PROJECTION AT $growthPercent% GROWTH:"
    override fun targetOpenNumber(k: Int) = "Open #$k"
    override fun targetProjectionBaseHint(baseSec: Int) = "Calculated from base time ${baseSec}s (or active schedule time)."
    override val targetQuickLock = "QUICK APP LOCK"
    override fun targetQuickLockDesc(appName: String) = "Lock \"$appName\" right now for:"
    override fun targetQuickLockFocusPrefix(appName: String) = "Focus: $appName"
    override val saveChangesButton = "SAVE CHANGES"
    override val removeFromProtectionButton = "REMOVE FROM PROTECTION"

    override val blockOverlayTitle = "APPLICATION BLOCKED"
    override val blockOverlayClose = "RETURN TO HOME SCREEN"
    override fun reinterventionPrompt(appName: String, timeStr: String) =
        "You have already spent $timeStr in $appName.\nDo you want to continue?"

    override fun formatSavedTime(minutes: Long): String {
        if (minutes <= 0) return "0 min"
        val days = minutes / 1440
        val hours = (minutes % 1440) / 60
        val mins = minutes % 60
        return when {
            days > 0 && hours > 0 -> "$days d $hours h"
            days > 0 -> "$days days"
            hours > 0 && mins > 0 -> "$hours h $mins min"
            hours > 0 -> "$hours hrs"
            else -> "$mins min"
        }
    }

    override fun formatDuration(totalMs: Long): String {
        val totalSecs = (totalMs / 1000L).coerceAtLeast(0L)
        val mins = totalSecs / 60L
        val secs = totalSecs % 60L
        return when {
            mins > 0 && secs > 0 -> "$mins min $secs s"
            mins > 0 -> "$mins min"
            else -> "$secs sec"
        }
    }
}

object RuStrings : AppStrings {
    override val navFocus = "ФОКУС"
    override val navBlock = "БЛОК"
    override val navStats = "СТАТИСТИКА"
    override val navConfig = "НАСТРОЙКИ"

    override val protectedSection = "ПОД ЗАЩИТОЙ"
    override val addAppButton = "+ ДОБАВИТЬ ПРИЛОЖЕНИЕ"
    override val pauseProtectionTitle = "ВРЕМЕННО ПРИОСТАНОВИТЬ ЗАЩИТУ"
    override val pause15m = "15 МИН"
    override val pause30m = "30 МИН"
    override val pause1h = "1 ЧАС"
    override val pauseForever = "БЕССРОЧНО"
    override val protectionPausedTitle = "ЗАЩИТА ПРИОСТАНОВЛЕНА"
    override val remainingPrefix = "Осталось:"
    override val resumeButton = "ВОЗОБНОВИТЬ"
    override val todayHeader = "СЕГОДНЯ"
    override val savedUnit = "сбережено"
    override val victoriesUnit = "побед"
    override val attemptsUnit = "открытий"
    override val noProtectedApps = "НЕТ ЗАЩИЩЕННЫХ ПРИЛОЖЕНИЙ"
    override val noProtectedAppsSubtitle = "Добавьте приложения, которые отвлекают вас от важных дел"

    override val selectAppTitle = "ВЫБЕРИТЕ ПРИЛОЖЕНИЕ"
    override val searchAppsPlaceholder = "Поиск по названию..."
    override val addAppItemButton = "ДОБАВИТЬ"
    override val cancelButton = "ОТМЕНА"
    override val noAppsAvailable = "НЕТ ДОСТУПНЫХ ПРИЛОЖЕНИЙ"

    override val settingsTitle = "НАСТРОЙКИ"
    override val languageSection = "ЯЗЫК ИНТЕРФЕЙСА"
    override val themeSection = "ЦВЕТОВАЯ ТЕМА"
    override val activeBadge = "АКТИВНА"
    override val sessionDurationSection = "СРЕДНЯЯ ДЛИТЕЛЬНОСТЬ СЕССИИ"
    override val sessionDurationDesc = "Используется для точного расчета сэкономленного времени при закрытии приложений"
    override val minutesUnit = "мин"
    override val secondsShort = "сек"
    override val secondsLabel = "СЕКУНДЫ"
    override val secondsUnitShort = "с"
    override val minutesShort = "м"
    override val hoursShort = "ч"
    override val statsOnOverlaySection = "СТАТИСТИКА НА ЭКРАНЕ ПАУЗЫ"
    override val statsOnOverlayDesc = "Показывать строку «Вы сберегли уже...» поверх анимации осознанности"
    override val onLabel = "ВКЛ"
    override val offLabel = "ВЫКЛ"
    override val systemPermissionsSection = "СИСТЕМНЫЕ РАЗРЕШЕНИЯ"
    override val permAccessibility = "СПЕЦИАЛЬНЫЕ ВОЗМОЖНОСТИ"
    override val permAccessibilityDesc = "Обнаружение запуска приложений"
    override val permOverlay = "ОКНО ПОВЕРХ ДРУГИХ"
    override val permOverlayDesc = "Экран осознанной паузы"
    override val permBattery = "РАБОТА В ФОНЕ"
    override val permBatteryDesc = "Защита от выгрузки системой"
    override val readyBadge = "ГОТОВО"
    override val enableButton = "ВКЛЮЧИТЬ"
    override val googleWarningTitle = "БЛОКИРОВКА НАСТРОЕК СИСТЕМОЙ ИЛИ GOOGLE?"
    override val googleWarningDesc = "Google Play Защита или система могут предупреждать о «потенциально опасном приложении» из-за механики отображения поверх других окон. При этом wattim на 100% офлайн и не имеет сетевых разрешений.\n\nЕсли пункт спец. возможностей заблокирован («Ограниченная настройка» на Android 13+):\n1. Перейдите в настройки приложения wattim в системе (кнопка ниже)\n2. Нажмите меню (⋮) в верхнем правом углу\n3. Выберите «Разрешить ограниченные настройки»"
    override val openAppSettingsButton = "ОТКРЫТЬ НАСТРОЙКИ WATTIM В ANDROID"
    override val privacySection = "БЕЗОПАСНОСТЬ И ПРИВАТНОСТЬ"
    override val privacyBullets = "• 100% ОФФЛАЙН (БЕЗ ДОСТУПА В ИНТЕРНЕТ)\n• ПОЛНОЕ ОТСУТСТВИЕ ТЕЛЕМЕТРИИ\n• ВСЕ ДАННЫЕ В ЛОКАЛЬНОЙ БАЗЕ УСТРОЙСТВА"

    override val onboardingTitle = "WATTIM НАСТРОЙКА"
    override val systemReadyTitle = "СИСТЕМА ГОТОВА"
    override val stepAccessibilityTitle = "СПЕЦИАЛЬНЫЕ ВОЗМОЖНОСТИ"
    override val stepAccessibilityDesc = "Необходимо для отслеживания запуска защищаемых приложений в реальном времени."
    override val stepOverlayTitle = "ОТОБРАЖЕНИЕ ПОВЕРХ ДРУГИХ ПРИЛОЖЕНИЙ"
    override val stepOverlayDesc = "Необходимо для показа полноэкранного экрана осознанности и дыхания перед входом."
    override val stepBatteryTitle = "РАБОТА В ФОНЕ (БАТАРЕЯ)"
    override val stepBatteryDesc = "Отключите оптимизацию батареи для wattim, чтобы Android не останавливал службу защиты."
    override val openAccessibilityButton = "ОТКРЫТЬ СПЕЦ. ВОЗМОЖНОСТИ"
    override val allowOverlayButton = "РАЗРЕШИТЬ ОТОБРАЖЕНИЕ"
    override val disableBatteryOptButton = "ОТКЛЮЧИТЬ ОПТИМИЗАЦИЮ БАТАРЕИ"
    override val startWattimButton = "ПЕРЕЙТИ В WATTIM"
    override val checkStatusButton = "ПРОВЕРИТЬ СТАТУС"

    override val phaseInhale = "Вдох"
    override val phaseHold = "Задержка"
    override val phaseExhale = "Выдох"
    override val phaseRest = "Покой"
    override val exitButton = "ВЫЙТИ"
    override val continueButton = "ПРОДОЛЖИТЬ"
    override val emergencyButton = "ЭКСТРЕННЫЙ ВХОД"
    override val emergencyDialogTitle = "Вы уверены?"
    override val emergencyDialogDesc = "Вы уверены, что хотите пропустить дыхательную паузу? Выберите действие:"
    override val emergencyEnterOnce = "Войти разово"
    override val emergencyPauseApp = "Приостановить защиту приложения"
    override val emergencyResumeBreath = "Вернуться к дыханию"
    override fun savedTimeOverlay(time: String) = "🌱 Вы уже сберегли $time жизни"

    override fun animationName(type: io.ronesec.android.domain.model.AnimationType): String = when (type) {
        io.ronesec.android.domain.model.AnimationType.FILL -> "Заполнение"
        io.ronesec.android.domain.model.AnimationType.PULSE -> "Дыхательная сфера"
        io.ronesec.android.domain.model.AnimationType.CIRCLE -> "Вихрь дзен"
        io.ronesec.android.domain.model.AnimationType.HORIZONTAL_SWEEP -> "Горизонтальная волна"
        io.ronesec.android.domain.model.AnimationType.VERTICAL_SWEEP -> "Вертикальная волна"
    }

    override val statsTitle = "СТАТИСТИКА"
    override val lifeTimeSavedCardTitle = "СЭКОНОМЛЕНО ВРЕМЕНИ ЖИЗНИ"
    override fun totalImpulsiveAvoided(count: Int) = "Всего предотвращено импульсивных сессий: $count"
    override val todaySaved = "Сегодня спасено:"
    override val todaySummaryTitle = "СВОДКА ЗА СЕГОДНЯ"
    override val metricOpenAttempts = "ПОПЫТОК ОТКРЫТИЯ"
    override val metricContinued = "ПРОДОЛЖЕНО"
    override val metricClosed = "ЗАКРЫТО"
    override val metricMindfulness = "ОСОЗНАННОСТЬ"
    override val perAppTitle = "ПО ПРИЛОЖЕНИЯМ"
    override val tableHeaderApp = "ПРИЛОЖЕНИЕ"
    override val tableHeaderOpen = "ОТКРЫТО"
    override val tableHeaderClosed = "ЗАКРЫТО"
    override val noActivityToday = "АКТИВНОСТЬ ЗА СЕГОДНЯ НЕ ЗАФИКСИРОВАНА"

    override val blocksTitle = "БЛОКИРОВКА"
    override val focusSessionActive = "СЕССИЯ ФОКУСА АКТИВНА"
    override fun blockedAppsCount(count: Int) = "Блокировка приложений: $count"
    override val stopSessionButton = "ОСТАНОВИТЬ СЕССИЮ"
    override val quickFocusSession = "БЫСТРАЯ СЕССИЯ ФОКУСА"
    override fun forAppsCount(selected: Int, total: Int) = "ДЛЯ ПРИЛОЖЕНИЙ ($selected/$total)"
    override val deselectAll = "Снять все"
    override val selectAll = "Выбрать все"
    override val defaultFocusSessionName = "Сессия фокуса"
    override val blockSchedulesTitle = "РАСПИСАНИЕ БЛОКИРОВОК"
    override val createScheduleButton = "+ СОЗДАТЬ РАСПИСАНИЕ"
    override val noSchedulesTitle = "НЕТ ЗАПЛАНИРОВАННЫХ БЛОКИРОВОК"
    override val noSchedulesSubtitle = "Создайте расписание, чтобы автоматически блокировать приложения в заданные часы."
    override val editButton = "РЕДАКТИРОВАТЬ"
    override val deleteButton = "УДАЛИТЬ"
    override val fullBlockBadge = "🔒 Полная блокировка"
    override val scheduledInterventionsBadge = "🧘 Интервенции по расписанию"
    override fun allAppsBadge(count: Int) = "Все приложения ($count)"
    override val noneSelectedLabel = "Не выбраны"
    override val blockedPrefix = "Заблокировано"
    override val interventionsPrefix = "Интервенции"
    override val editScheduleTitle = "РЕДАКТИРОВАНИЕ РАСПИСАНИЯ"
    override val newScheduleTitle = "НОВОЕ РАСПИСАНИЕ"
    override val scheduleNameLabel = "НАЗВАНИЕ РАСПИСАНИЯ"
    override val scheduleTypeLabel = "ТИП РАСПИСАНИЯ"
    override val hardBlockTypeTitle = "ПОЛНАЯ БЛОКИРОВКА"
    override val hardBlockTypeDesc = "Приложения полностью блокируются в указанные часы."
    override val customInterventionsTitle = "ОСОБЫЕ ИНТЕРВЕНЦИИ"
    override val customInterventionsDesc = "Приложения открываются через экран паузы с индивидуальными рамками."
    override val selectedBadge = "ВЫБРАНО"
    override val timeWindowPrefix = "ПРОМЕЖУТОК"
    override val presetWork = "Работа (9-18)"
    override val presetNight = "Ночь (23-7)"
    override val presetMorning = "Утро (7-12)"
    override val presetEvening = "Вечер (18-23)"
    override val startLabel = "НАЧАЛО"
    override val endLabel = "КОНЕЦ"
    override val hoursLabel = "ЧАСЫ"
    override val minutesLabel = "МИНУТЫ"
    override val daysOfWeekTitle = "ДНИ НЕДЕЛИ"
    override val weekdaysBadge = "БУДНИ"
    override val weekendsBadge = "ВЫХОДНЫЕ"
    override val allDaysBadge = "ВСЕ ДНИ"
    override fun dayShortName(day: java.time.DayOfWeek): String = when (day) {
        java.time.DayOfWeek.MONDAY -> "Пн"
        java.time.DayOfWeek.TUESDAY -> "Вт"
        java.time.DayOfWeek.WEDNESDAY -> "Ср"
        java.time.DayOfWeek.THURSDAY -> "Чт"
        java.time.DayOfWeek.FRIDAY -> "Пт"
        java.time.DayOfWeek.SATURDAY -> "Сб"
        java.time.DayOfWeek.SUNDAY -> "Вс"
    }
    override fun dayChipName(day: java.time.DayOfWeek): String = when (day) {
        java.time.DayOfWeek.MONDAY -> "ПН"
        java.time.DayOfWeek.TUESDAY -> "ВТ"
        java.time.DayOfWeek.WEDNESDAY -> "СР"
        java.time.DayOfWeek.THURSDAY -> "ЧТ"
        java.time.DayOfWeek.FRIDAY -> "ПТ"
        java.time.DayOfWeek.SATURDAY -> "СБ"
        java.time.DayOfWeek.SUNDAY -> "ВС"
    }
    override val noProtectedAppsBlocksHint = "Нет защищенных приложений. Добавьте их во вкладке «Приложения»."
    override val saveButton = "СОХРАНИТЬ"
    override val defaultScheduleNameBlock = "Блокировка"
    override val defaultScheduleNameIntervention = "Интервенции"
    override fun customRulesTitle(appName: String) = "Индивидуальные рамки: $appName"
    override val pauseDurationTitle = "ДЛИТЕЛЬНОСТЬ ПАУЗЫ"
    override val repeatInterventionTitle = "ПОВТОР ИНТЕРВЕНЦИИ"
    override val optionOff = "Выкл"

    override val previewSavedTime = "Предпросмотр: вы сберегли 2 дня жизни"
    override val targetProtectionStatus = "СТАТУС ЗАЩИТЫ"
    override val targetStatusActive = "АКТИВЕН"
    override val targetStatusDisabled = "ОТКЛЮЧЕН"
    override val targetMindfulnessPhrase = "ФРАЗА ОСОЗНАННОСТИ"
    override val targetDefaultPhrase = "Сделайте глубокий вдох"
    override val targetAnimationType = "ТИП АНИМАЦИИ"
    override val targetPauseDuration = "ДЛИТЕЛЬНОСТЬ ПАУЗЫ (ВДОХ И ВЫДОХ)"
    override val targetPreviewAnimation = "ПРЕДПРОСМОТР АНИМАЦИИ"
    override val targetReintercept = "ПОВТОРНЫЙ ПЕРЕХВАТ"
    override val targetCustomOption = "СВОЁ"
    override val targetCustomInterval = "СВОЙ ИНТЕРВАЛ"
    override val targetQuickPresets = "БЫСТРЫЙ ВЫБОР"
    override val targetReinterceptOffDesc = "Повторный вопрос во время непрерывной работы приложения отключен."
    override fun targetReinterceptOnDesc(timeStr: String) = "Через $timeStr непрерывного использования появится экран с вопросом «Хотите продолжить?»."
    override val targetQuickReturn = "БЫСТРЫЙ ВОЗВРАТ (БЕЗ ПАУЗЫ)"
    override val targetQuickReturn0s = "0с — новая анимация будет показываться сразу при каждом выходе и повторном входе."
    override fun targetQuickReturnGrace(sec: Long) = "Если вернуться в приложение в течение $sec сек, пауза показываться не будет."
    override val targetExponentialGrowth = "ЭКСПОНЕНЦИАЛЬНЫЙ РОСТ"
    override val targetExponentialGrowthDesc = "Увеличение времени паузы при частых повторных открытиях приложения за выбранный период."
    override val targetGrowthPercent = "ПРОЦЕНТ РОСТА НА КАЖДОЕ ОТКРЫТИЕ"
    override val targetRollingWindow = "ПЕРИОД УЧЕТА (СКОЛЬЗЯЩЕЕ ОКНО)"
    override fun targetProjectionTitle(growthPercent: Int) = "ПРЕДПРОСЧЕТ ЗАДЕРЖКИ ПРИ $growthPercent% РОСТА:"
    override fun targetOpenNumber(k: Int) = "$k-е открытие"
    override fun targetProjectionBaseHint(baseSec: Int) = "Расчет отталкивается от базового времени ${baseSec}с (или времени из активного расписания)."
    override val targetQuickLock = "БЫСТРАЯ БЛОКИРОВКА ПРИЛОЖЕНИЯ"
    override fun targetQuickLockDesc(appName: String) = "Заблокировать «$appName» прямо сейчас на выбранное время:"
    override fun targetQuickLockFocusPrefix(appName: String) = "Фокус: $appName"
    override val saveChangesButton = "СОХРАНИТЬ ИЗМЕНЕНИЯ"
    override val removeFromProtectionButton = "УДАЛИТЬ ИЗ ЗАЩИТЫ"

    override val blockOverlayTitle = "ПРИЛОЖЕНИЕ ЗАБЛОКИРОВАНО"
    override val blockOverlayClose = "ВЕРНУТЬСЯ НА ГЛАВНЫЙ ЭКРАН"
    override fun reinterventionPrompt(appName: String, timeStr: String) =
        "Вы уже провели в $appName $timeStr.\nХотите продолжить?"

    override fun formatSavedTime(minutes: Long): String {
        if (minutes <= 0) return "0 мин."
        val days = minutes / 1440
        val hours = (minutes % 1440) / 60
        val mins = minutes % 60
        return when {
            days > 0 && hours > 0 -> "$days дн. $hours ч. жизни"
            days > 0 -> "$days дн. жизни"
            hours > 0 && mins > 0 -> "$hours ч. $mins мин."
            hours > 0 -> "$hours ч."
            else -> "$mins мин."
        }
    }

    override fun formatDuration(totalMs: Long): String = io.ronesec.android.domain.util.TimeFormatUtils.formatDurationRu(totalMs)
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
