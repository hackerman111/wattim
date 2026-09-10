package io.ronesec.android.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.ronesec.android.data.entity.AppSettingsEntity

@Dao
interface AppSettingsDao {
    @Query("SELECT * FROM app_settings WHERE id = 1")
    suspend fun getSettings(): AppSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setSettings(settings: AppSettingsEntity)

    @Query("UPDATE app_settings SET pauseKind = :kind, pauseUntil = :until, rowVersion = rowVersion + 1 WHERE id = 1")
    suspend fun updatePause(kind: String, until: Long?): Int

    @Query("UPDATE app_settings SET themeId = :themeId, rowVersion = rowVersion + 1 WHERE id = 1")
    suspend fun updateTheme(themeId: String): Int

    @Query("UPDATE app_settings SET language = :language, rowVersion = rowVersion + 1 WHERE id = 1")
    suspend fun updateLanguage(language: String): Int

    @Query("UPDATE app_settings SET savedSessionMinutes = :savedSessionMinutes, rowVersion = rowVersion + 1 WHERE id = 1")
    suspend fun updateSavedSessionMinutes(savedSessionMinutes: Int): Int

    @Query("UPDATE app_settings SET showOverlayStats = :show, rowVersion = rowVersion + 1 WHERE id = 1")
    suspend fun updateShowOverlayStats(show: Boolean): Int
}
