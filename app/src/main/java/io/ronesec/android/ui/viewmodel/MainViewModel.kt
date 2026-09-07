package io.ronesec.android.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.ronesec.android.RonesecApplication
import io.ronesec.android.domain.model.AttemptOutcome
import io.ronesec.android.domain.model.BlockSchedule
import io.ronesec.android.domain.model.BlockSession
import io.ronesec.android.domain.model.OpenAttempt
import io.ronesec.android.domain.model.TargetApp
import io.ronesec.android.ui.theme.AppTheme
import io.ronesec.android.ui.theme.TerminalAccent
import io.ronesec.android.util.PermissionHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.temporal.ChronoUnit

data class InstalledAppInfo(
    val packageName: String,
    val label: String
)

data class TodayStats(
    val openAttempts: Int = 0,
    val continued: Int = 0,
    val closed: Int = 0,
    val avoidedPercent: Int = 0,
    val savedTimeFormatted: String = "0 мин.",
    val allTimeAvoided: Int = 0,
    val allTimeSavedFormatted: String = "0 мин."
)

data class AppStatRow(
    val packageName: String,
    val displayName: String,
    val openCount: Int,
    val closedCount: Int
)

fun formatSavedTime(minutes: Long): String {
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

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as RonesecApplication).repository
    private val packageManager: PackageManager = application.packageManager

    val targets: StateFlow<List<TargetApp>> = repository.getTargetsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeBlockSessions: StateFlow<List<BlockSession>> = repository.getActiveSessionsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val blockSchedules: StateFlow<List<BlockSchedule>> = repository.getSchedulesFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Attempts from start of today
    private val todayMidnight = Instant.now().truncatedTo(ChronoUnit.DAYS).toEpochMilli()

    val todayAttempts: StateFlow<List<OpenAttempt>> = repository.getRecentAttemptsFlow(todayMidnight)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allAttempts: StateFlow<List<OpenAttempt>> = repository.getAllAttemptsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val sessionMinutes: StateFlow<Int> = repository.getSettingFlow("session_minutes")
        .map { it?.toIntOrNull() ?: 7 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 7)

    val todayStats: StateFlow<TodayStats> = combine(todayAttempts, allAttempts, sessionMinutes) { todayList, allList, minsPerSession ->
        val total = todayList.size
        val continued = todayList.count { it.outcome == AttemptOutcome.CONTINUED }
        val closed = todayList.count { it.outcome == AttemptOutcome.ABANDONED || it.outcome == AttemptOutcome.BLOCKED }
        val avoided = if (total > 0) ((closed.toFloat() / total) * 100).toInt() else 0
        val todayMinutes = closed.toLong() * minsPerSession

        val allAvoided = allList.count { it.outcome == AttemptOutcome.ABANDONED || it.outcome == AttemptOutcome.BLOCKED }
        val allMinutes = allAvoided.toLong() * minsPerSession

        TodayStats(
            openAttempts = total,
            continued = continued,
            closed = closed,
            avoidedPercent = avoided,
            savedTimeFormatted = formatSavedTime(todayMinutes),
            allTimeAvoided = allAvoided,
            allTimeSavedFormatted = formatSavedTime(allMinutes)
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TodayStats())

    val appStats: StateFlow<List<AppStatRow>> = todayAttempts.map { attempts ->
        val targetMap = targets.value.associateBy { it.packageName }
        attempts.groupBy { it.packageName }.map { (pkg, list) ->
            val total = list.size
            val closed = list.count { it.outcome == AttemptOutcome.ABANDONED || it.outcome == AttemptOutcome.BLOCKED }
            val name = targetMap[pkg]?.displayName ?: getAppDisplayName(pkg)
            AppStatRow(
                packageName = pkg,
                displayName = name,
                openCount = total,
                closedCount = closed
            )
        }.sortedByDescending { it.openCount }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val currentTheme: StateFlow<AppTheme> = repository.getSettingFlow("app_theme")
        .map { AppTheme.fromId(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppTheme.NORD)

    val currentAccent: StateFlow<TerminalAccent> = currentTheme
        .map { TerminalAccent.fromName(it.palette.accent.toString()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TerminalAccent.CYAN)

    val showSavedTimeOnOverlay: StateFlow<Boolean> = repository.getSettingFlow("show_saved_time_stats")
        .map { it == null || it == "true" }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun toggleShowSavedTimeOnOverlay(enabled: Boolean) {
        viewModelScope.launch {
            repository.setSetting("show_saved_time_stats", enabled.toString())
        }
    }

    private val _permissionsGranted = MutableStateFlow(false)
    val permissionsGranted: StateFlow<Boolean> = _permissionsGranted.asStateFlow()

    private val _installedApps = MutableStateFlow<List<InstalledAppInfo>>(emptyList())
    val installedApps: StateFlow<List<InstalledAppInfo>> = _installedApps.asStateFlow()

    init {
        checkPermissions()
        loadInstalledApps()
    }

    fun checkPermissions() {
        _permissionsGranted.value = PermissionHelper.areAllPermissionsGranted(getApplication())
    }

    fun setTheme(theme: AppTheme) {
        viewModelScope.launch {
            repository.setSetting("app_theme", theme.id)
        }
    }

    fun setSessionMinutes(minutes: Int) {
        viewModelScope.launch {
            repository.setSetting("session_minutes", minutes.toString())
        }
    }

    fun setAccent(accent: TerminalAccent) {
        viewModelScope.launch {
            repository.setSetting("accent_color", accent.name)
        }
    }

    fun toggleTarget(target: TargetApp, enabled: Boolean) {
        viewModelScope.launch {
            repository.saveTarget(target.copy(enabled = enabled))
        }
    }

    fun saveTarget(target: TargetApp) {
        viewModelScope.launch {
            repository.saveTarget(target)
        }
    }

    fun deleteTarget(packageName: String) {
        viewModelScope.launch {
            repository.deleteTarget(packageName)
        }
    }

    fun startHardBlock(name: String, durationMinutes: Int, packages: Set<String>) {
        viewModelScope.launch {
            repository.startHardBlock(name, durationMinutes, packages)
        }
    }

    fun stopHardBlock(sessionId: Long) {
        viewModelScope.launch {
            repository.stopHardBlock(sessionId)
        }
    }

    fun saveSchedule(schedule: BlockSchedule) {
        viewModelScope.launch {
            repository.saveSchedule(schedule)
        }
    }

    fun deleteSchedule(id: Long) {
        viewModelScope.launch {
            repository.deleteSchedule(id)
        }
    }

    fun addTargetFromPackage(pkg: String, label: String) {
        viewModelScope.launch {
            repository.saveTarget(
                TargetApp(
                    packageName = pkg,
                    displayName = label,
                    enabled = true
                )
            )
        }
    }

    private fun loadInstalledApps() {
        viewModelScope.launch(Dispatchers.IO) {
            val apps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
            val filtered = apps.filter { app ->
                // Filter out non-launchable system internals, but allow standard user apps
                (app.flags and ApplicationInfo.FLAG_SYSTEM) == 0 ||
                        packageManager.getLaunchIntentForPackage(app.packageName) != null
            }.map { app ->
                InstalledAppInfo(
                    packageName = app.packageName,
                    label = packageManager.getApplicationLabel(app).toString()
                )
            }.sortedBy { it.label.lowercase() }
            _installedApps.value = filtered
        }
    }

    private fun getAppDisplayName(pkg: String): String {
        return try {
            val info = packageManager.getApplicationInfo(pkg, 0)
            packageManager.getApplicationLabel(info).toString()
        } catch (e: Exception) {
            pkg.substringAfterLast('.')
        }
    }
}
