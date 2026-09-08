package io.ronesec.android.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import io.ronesec.android.domain.model.AttemptOutcome
import io.ronesec.android.domain.model.OpenAttempt
import java.time.Instant

@Entity(
    tableName = "open_attempts",
    indices = [
        Index(value = ["packageName", "timestamp"]),
        Index(value = ["timestamp", "outcome"])
    ]
)
data class OpenAttemptEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val packageName: String,
    val timestamp: Long,
    val outcome: AttemptOutcome
) {
    fun toDomain(): OpenAttempt = OpenAttempt(
        id = id,
        packageName = packageName,
        timestamp = Instant.ofEpochMilli(timestamp),
        outcome = outcome
    )

    companion object {
        fun fromDomain(domain: OpenAttempt): OpenAttemptEntity = OpenAttemptEntity(
            id = domain.id,
            packageName = domain.packageName,
            timestamp = domain.timestamp.toEpochMilli(),
            outcome = domain.outcome
        )
    }
}
