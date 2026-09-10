package io.ronesec.android.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "block_schedules")
data class BlockScheduleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val name: String,
    val weekdayMask: Int,
    val startMinute: Int,
    val endMinute: Int,
    val enabled: Boolean = true,
    val type: String, // "HARD_BLOCK" or "INTERVENTION"
    val rowVersion: Long = 1L
)

@Entity(
    tableName = "schedule_targets",
    primaryKeys = ["scheduleId", "packageName"],
    foreignKeys = [
        ForeignKey(
            entity = BlockScheduleEntity::class,
            parentColumns = ["id"],
            childColumns = ["scheduleId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = TargetAppEntity::class,
            parentColumns = ["packageName"],
            childColumns = ["packageName"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["packageName"])
    ]
)
data class ScheduleTargetCrossRef(
    val scheduleId: Long,
    val packageName: String
)

@Entity(
    tableName = "schedule_overrides",
    primaryKeys = ["scheduleId", "packageName"],
    foreignKeys = [
        ForeignKey(
            entity = ScheduleTargetCrossRef::class,
            parentColumns = ["scheduleId", "packageName"],
            childColumns = ["scheduleId", "packageName"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class ScheduleOverrideEntity(
    val scheduleId: Long,
    val packageName: String,
    val durationMs: Long? = null,
    val reinterventionMs: Long? = null
)
