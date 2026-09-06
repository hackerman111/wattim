package io.ronesec.android.domain.model

data class InterventionConfig(
    val phrase: String = "Сделайте глубокий вдох",
    val animation: AnimationType = AnimationType.FILL,
    val durationMs: Long = 8_000L,
    val reinterventionMs: Long? = 300_000L,
    val quickReturnGraceMs: Long = 60_000L
)
