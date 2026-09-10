package io.ronesec.android.ui.blocks

import io.ronesec.domain.model.ScheduleType
import io.ronesec.domain.model.TargetConfig
import java.time.Instant

data class ActiveBlockSessionUiModel(
    val id: String,
    val name: String,
    val startTime: Instant,
    val endTime: Instant,
    val targetPackages: Set<String>,
    val remainingSeconds: Long
) {
    val isExpired: Boolean
        get() = remainingSeconds <= 0
}

data class ScheduleItemUiModel(
    val id: Long,
    val name: String,
    val weekdayMask: Int,
    val daysSummary: String,
    val startMinute: Int,
    val endMinute: Int,
    val timeSummary: String,
    val isOvernight: Boolean,
    val enabled: Boolean,
    val type: ScheduleType,
    val targetPackages: Set<String>,
    val targetsSummary: String,
    val overridesCount: Int
)

data class BlocksUiState(
    val activeSession: ActiveBlockSessionUiModel? = null,
    val protectedApps: List<TargetConfig> = emptyList(),
    val selectedPackagesForFocus: Set<String> = emptySet(),
    val selectedFocusDurationMs: Long = 30 * 60 * 1000L,
    val schedules: List<ScheduleItemUiModel> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
) {
    val isQuickFocusEnabled: Boolean
        get() = protectedApps.isNotEmpty() && selectedPackagesForFocus.isNotEmpty() && !isLoading

    val hasActiveSession: Boolean
        get() = activeSession != null && !activeSession.isExpired
}

object BlocksFormatters {
    private val DAY_NAMES = listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")

    fun formatDaysSummary(mask: Int): String {
        if (mask == 0x7F) return "EVERY DAY"
        if (mask == 0x1F) return "WEEKDAYS"
        if (mask == 0x60) return "WEEKENDS"

        val days = mutableListOf<String>()
        for (i in 0..6) {
            if ((mask and (1 shl i)) != 0) {
                days.add(DAY_NAMES[i])
            }
        }
        return if (days.isEmpty()) "NONE" else days.joinToString(", ")
    }

    fun formatTimeRange(startMinute: Int, endMinute: Int): String {
        val startH = startMinute / 60
        val startM = startMinute % 60
        val endH = endMinute / 60
        val endM = endMinute % 60
        return "%02d:%02d – %02d:%02d".format(startH, startM, endH, endM)
    }

    fun formatRemainingTime(remainingSec: Long): String {
        val sec = maxOf(0L, remainingSec)
        val hours = sec / 3600
        val minutes = (sec % 3600) / 60
        val seconds = sec % 60
        return "%02d:%02d:%02d".format(hours, minutes, seconds)
    }

    fun formatTargetsSummary(
        targetPackages: Set<String>,
        allTargets: Map<String, TargetConfig>
    ): String {
        if (targetPackages.isEmpty()) return "NO APPLICATIONS"
        if (allTargets.isNotEmpty() && targetPackages.size >= allTargets.size && targetPackages.containsAll(allTargets.keys)) {
            return "ALL APPLICATIONS (${targetPackages.size})"
        }
        val names = targetPackages.map { pkg ->
            allTargets[pkg]?.displayName?.ifBlank { pkg } ?: pkg
        }.sorted()
        return if (names.size <= 2) {
            names.joinToString(", ")
        } else {
            "${names.take(2).joinToString(", ")} +${names.size - 2}"
        }
    }
}
