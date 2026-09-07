package io.ronesec.android.domain.model

data class InterventionConfig(
    val phrase: String = "Сделайте глубокий вдох",
    val animation: AnimationType = AnimationType.FILL,
    val durationMs: Long = 8_000L,
    val reinterventionMs: Long? = 300_000L,
    val quickReturnGraceMs: Long = 0L,
    val exponentialGrowthEnabled: Boolean = false,
    val growthPercent: Int = 20,
    val growthPeriodMinutes: Int = 60
)
