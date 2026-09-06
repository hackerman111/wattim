package io.ronesec.android.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import io.ronesec.android.domain.model.AccessGrant
import java.time.Instant

@Entity(tableName = "access_grants")
data class AccessGrantEntity(
    @PrimaryKey
    val packageName: String,
    val createdAt: Long,
    val expiresAt: Long?
) {
    fun toDomain(): AccessGrant = AccessGrant(
        packageName = packageName,
        createdAt = Instant.ofEpochMilli(createdAt),
        expiresAt = expiresAt?.let { Instant.ofEpochMilli(it) }
    )

    companion object {
        fun fromDomain(domain: AccessGrant): AccessGrantEntity = AccessGrantEntity(
            packageName = domain.packageName,
            createdAt = domain.createdAt.toEpochMilli(),
            expiresAt = domain.expiresAt?.toEpochMilli()
        )
    }
}
