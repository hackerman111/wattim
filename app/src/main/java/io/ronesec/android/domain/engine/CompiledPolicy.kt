package io.ronesec.android.domain.engine

import io.ronesec.android.domain.model.BlockSchedule
import io.ronesec.android.domain.model.InterventionConfig
import io.ronesec.android.domain.model.ScheduleType
import java.time.LocalTime

data class NormalizedScheduleInterval(
    val schedule: BlockSchedule,
    val scheduleType: ScheduleType,
    val isOvernight: Boolean,
    val start: LocalTime,
    val end: LocalTime
)

data class CompiledAppPolicy(
    val packageName: String,
    val enabled: Boolean,
    val baseIntervention: InterventionConfig,
    val schedules: List<NormalizedScheduleInterval>
)
