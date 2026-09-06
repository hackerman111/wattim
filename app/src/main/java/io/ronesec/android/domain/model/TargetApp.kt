package io.ronesec.android.domain.model

data class TargetApp(
    val packageName: String,
    val displayName: String,
    val enabled: Boolean = true,
    val intervention: InterventionConfig = InterventionConfig()
)
