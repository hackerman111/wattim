package io.ronesec.android.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "target_apps")
data class TargetAppEntity(
    @PrimaryKey
    val packageName: String,
    val displayName: String,
    val enabled: Boolean = true,
    val phrase: String = "Сделай глубокий вдох и выдох",
    val animation: String = "FILL",
    val durationMs: Long = 8_000L,
    val reinterventionMs: Long = 300_000L,
    val quickReturnGraceMs: Long = 0L,
    val growthEnabled: Boolean = false,
    val growthPercent: Int = 20,
    val growthWindowMs: Long = 3_600_000L,
    val rowVersion: Long = 1L
)
