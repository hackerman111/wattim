package io.ronesec.android.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import io.ronesec.android.domain.model.BlockSchedule
import java.time.DayOfWeek
import java.time.LocalTime

@Entity(tableName = "block_schedules")
data class BlockScheduleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val daysOfWeek: String, // e.g. "MONDAY,TUESDAY,WEDNESDAY"
    val startHour: Int,
    val startMinute: Int,
    val endHour: Int,
    val endMinute: Int,
    val packages: String, // Comma-separated package names
    val enabled: Boolean
) {
    fun toDomain(): BlockSchedule = BlockSchedule(
        id = id,
        name = name,
        days = if (daysOfWeek.isBlank()) emptySet() else daysOfWeek.split(",").map { DayOfWeek.valueOf(it) }.toSet(),
        start = LocalTime.of(startHour, startMinute),
        end = LocalTime.of(endHour, endMinute),
        packages = if (packages.isBlank()) emptySet() else packages.split(",").toSet(),
        enabled = enabled
    )

    companion object {
        fun fromDomain(domain: BlockSchedule): BlockScheduleEntity = BlockScheduleEntity(
            id = domain.id,
            name = domain.name,
            daysOfWeek = domain.days.joinToString(",") { it.name },
            startHour = domain.start.hour,
            startMinute = domain.start.minute,
            endHour = domain.end.hour,
            endMinute = domain.end.minute,
            packages = domain.packages.joinToString(","),
            enabled = domain.enabled
        )
    }
}
