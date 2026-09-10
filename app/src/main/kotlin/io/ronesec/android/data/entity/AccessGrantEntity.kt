package io.ronesec.android.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "access_grants",
    indices = [
        Index(value = ["grantId"], unique = true),
        Index(value = ["expiresAt"])
    ]
)
data class AccessGrantEntity(
    @PrimaryKey
    val packageName: String,
    val grantId: String,
    val origin: String,
    val createdAt: Long,
    val expiresAt: Long,
    val grantVersion: Long = 1L
)
