package io.ronesec.android.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "open_attempts",
    indices = [
        Index(value = ["sessionId", "cycle"], unique = true),
        Index(value = ["packageName", "kind", "timestamp"]),
        Index(value = ["timestamp", "outcome"])
    ]
)
data class OpenAttemptEntity(
    @PrimaryKey
    val attemptId: String,
    val sessionId: String,
    val cycle: Int,
    val packageName: String,
    val displayNameAtAttempt: String,
    val generation: Long,
    val kind: String,
    val timestamp: Long,
    val outcome: String? = null,
    val resolvedAt: Long? = null
)
