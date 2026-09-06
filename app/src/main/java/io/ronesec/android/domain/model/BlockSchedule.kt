package io.ronesec.android.domain.model

import java.time.DayOfWeek
import java.time.LocalTime

data class BlockSchedule(
    val id: Long = 0,
    val name: String,
    val days: Set<DayOfWeek>,
    val start: LocalTime,
    val end: LocalTime,
    val packages: Set<String>,
    val enabled: Boolean = true
)
