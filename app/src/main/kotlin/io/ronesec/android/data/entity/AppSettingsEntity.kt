package io.ronesec.android.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_settings")
data class AppSettingsEntity(
    @PrimaryKey
    val id: Int = 1,
    val themeId: String = "NORD",
    val language: String = "AUTO",
    val savedSessionMinutes: Int = 7,
    val showOverlayStats: Boolean = true,
    val pauseKind: String = "NONE", // "NONE", "UNTIL", "INDEFINITE"
    val pauseUntil: Long? = null,
    val customEmergencyMinutes: Int? = null,
    val rowVersion: Long = 1L
)

@Entity(tableName = "policy_revision")
data class PolicyRevisionEntity(
    @PrimaryKey
    val id: Int = 1,
    val revision: Long = 1L
)
