package io.ronesec.android.ui.target

import io.ronesec.android.ui.designsystem.TerminalTab
import io.ronesec.domain.model.AnimationMode
import io.ronesec.domain.model.BackoffConfig
import io.ronesec.domain.model.TargetConfig

data class TargetSettingsDraft(
    val packageName: String,
    val displayName: String,
    val enabled: Boolean = true,
    val phrase: String = TargetConfig.DEFAULT_PHRASE,
    val animation: AnimationMode = AnimationMode.FILL,
    val durationSeconds: Int = 8,
    val reinterventionMode: ReinterventionChoice = ReinterventionChoice.MIN_5,
    val customReinterventionMinutes: Int = 5,
    val customReinterventionSeconds: Int = 0,
    val quickReturnGraceMs: Long = 0L,
    val backoffEnabled: Boolean = false,
    val backoffPercent: Int = 20,
    val backoffWindowMs: Long = 60 * 60 * 1000L,
    val baseRowVersion: Long = 1L
)

enum class ReinterventionChoice(val labelResName: String, val durationMs: Long?) {
    OFF("reintervention_off", 0L),
    MIN_1("1M", 60 * 1000L),
    MIN_3("3M", 3 * 60 * 1000L),
    MIN_5("5M", 5 * 60 * 1000L),
    MIN_10("10M", 10 * 60 * 1000L),
    CUSTOM("reintervention_custom", null);

    companion object {
        fun fromDurationMs(ms: Long): Pair<ReinterventionChoice, Pair<Int, Int>> {
            return when (ms) {
                0L -> Pair(OFF, Pair(0, 0))
                60_000L -> Pair(MIN_1, Pair(1, 0))
                180_000L -> Pair(MIN_3, Pair(3, 0))
                300_000L -> Pair(MIN_5, Pair(5, 0))
                600_000L -> Pair(MIN_10, Pair(10, 0))
                else -> {
                    val totalSec = (ms / 1000L).toInt()
                    val m = totalSec / 60
                    val s = totalSec % 60
                    Pair(CUSTOM, Pair(m, s))
                }
            }
        }
    }
}

data class TargetSettingsUiState(
    val packageName: String = "",
    val displayName: String = "",
    val originTab: TerminalTab = TerminalTab.APPS,
    val canQuickLock: Boolean = false,
    val draft: TargetSettingsDraft = TargetSettingsDraft("", ""),
    val isLoading: Boolean = false,
    val isStale: Boolean = false,
    val isSaved: Boolean = false,
    val isRemoved: Boolean = false,
    val isQuickLocked: Boolean = false,
    val isPreviewOpen: Boolean = false,
    val errorMessage: String? = null,
    val calculatedDelays: List<Long> = emptyList()
)
