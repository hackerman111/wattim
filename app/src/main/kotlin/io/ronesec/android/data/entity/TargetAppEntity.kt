package io.ronesec.android.data.entity

import androidx.room.Entity
import androidx.room.ColumnInfo
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
    val rowVersion: Long = 1L,
    @ColumnInfo(defaultValue = "0") val twoStageUnlock: Boolean = false,
    @ColumnInfo(defaultValue = "4") val unlockCodeLength: Int = 4,
    @ColumnInfo(defaultValue = "0") val requireEmergencyCode: Boolean = false,
    @ColumnInfo(defaultValue = "0") val randomDurationEnabled: Boolean = false,
    @ColumnInfo(defaultValue = "8000") val randomMaxDurationMs: Long = 8_000L,
    @ColumnInfo(defaultValue = "0") val attentionChecksEnabled: Boolean = false,
    @ColumnInfo(defaultValue = "1") val attentionCheckCount: Int = 1,
    @ColumnInfo(defaultValue = "4") val attentionCheckCodeLength: Int = 4,
    @ColumnInfo(defaultValue = "5000") val attentionCheckTimeoutMs: Long = 5_000L
)
