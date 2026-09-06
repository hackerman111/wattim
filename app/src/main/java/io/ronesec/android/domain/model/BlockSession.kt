package io.ronesec.android.domain.model

import java.time.Instant

data class BlockSession(
    val id: Long = 0,
    val name: String = "FOCUS SESSION",
    val startTime: Instant,
    val endTime: Instant,
    val active: Boolean = true,
    val packages: Set<String>
)
