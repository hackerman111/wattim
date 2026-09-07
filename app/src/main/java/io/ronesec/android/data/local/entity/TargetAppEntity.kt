package io.ronesec.android.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import io.ronesec.android.domain.model.AnimationType
import io.ronesec.android.domain.model.InterventionConfig
import io.ronesec.android.domain.model.TargetApp

@Entity(tableName = "target_apps")
data class TargetAppEntity(
    @PrimaryKey
    val packageName: String,
    val displayName: String,
    val enabled: Boolean,
    val phrase: String,
    val animation: AnimationType,
    val durationMs: Long,
    val reinterventionMs: Long?,
    val quickReturnGraceMs: Long,
    val exponentialGrowthEnabled: Boolean = false,
    val growthPercent: Int = 20,
    val growthPeriodMinutes: Int = 60
) {
    fun toDomain(): TargetApp = TargetApp(
        packageName = packageName,
        displayName = displayName,
        enabled = enabled,
        intervention = InterventionConfig(
            phrase = phrase,
            animation = animation,
            durationMs = durationMs,
            reinterventionMs = reinterventionMs,
            quickReturnGraceMs = quickReturnGraceMs,
            exponentialGrowthEnabled = exponentialGrowthEnabled,
            growthPercent = growthPercent,
            growthPeriodMinutes = growthPeriodMinutes
        )
    )

    companion object {
        fun fromDomain(domain: TargetApp): TargetAppEntity = TargetAppEntity(
            packageName = domain.packageName,
            displayName = domain.displayName,
            enabled = domain.enabled,
            phrase = domain.intervention.phrase,
            animation = domain.intervention.animation,
            durationMs = domain.intervention.durationMs,
            reinterventionMs = domain.intervention.reinterventionMs,
            quickReturnGraceMs = domain.intervention.quickReturnGraceMs,
            exponentialGrowthEnabled = domain.intervention.exponentialGrowthEnabled,
            growthPercent = domain.intervention.growthPercent,
            growthPeriodMinutes = domain.intervention.growthPeriodMinutes
        )
    }
}
