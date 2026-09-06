package io.ronesec.android.domain.model

import java.time.Instant

data class AccessGrant(
    val packageName: String,
    val createdAt: Instant,
    val expiresAt: Instant?
)
