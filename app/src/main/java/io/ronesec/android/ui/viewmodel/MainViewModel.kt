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
import io.ronesec.android.ui.theme.TerminalAccent
import io.ronesec.android.util.PermissionHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    val avoidedPercent: Int = 0
)

data class AppStatRow(
    val packageName: String,
    val displayName: String,
    val openCount: Int,
    val closedCount: Int
)

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

    val todayStats: StateFlow<TodayStats> = todayAttempts.map { list ->
        val total = list.size
        val continued = list.count { it.outcome == AttemptOutcome.CONTINUED }
        val closed = list.count { it.outcome == AttemptOutcome.ABANDONED || it.outcome == AttemptOutcome.BLOCKED }
        val avoided = if (total > 0) ((closed.toFloat() / total) * 100).toInt() else 0
        TodayStats(
            openAttempts = total,
            continued = continued,
            closed = closed,
            avoidedPercent = avoided
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

    val currentAccent: StateFlow<TerminalAccent> = repository.getSettingFlow("accent_color")
        .map { TerminalAccent.fromName(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TerminalAccent.CYAN)

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
