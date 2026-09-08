package io.ronesec.android.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import io.ronesec.android.data.local.dao.AccessGrantDao
import io.ronesec.android.data.local.dao.BlockDao
import io.ronesec.android.data.local.dao.OpenAttemptDao
import io.ronesec.android.data.local.dao.SettingsDao
import io.ronesec.android.data.local.dao.TargetAppDao
import io.ronesec.android.data.local.entity.AccessGrantEntity
import io.ronesec.android.data.local.entity.AppSettingEntity
import io.ronesec.android.data.local.entity.BlockScheduleEntity
import io.ronesec.android.data.local.entity.BlockSessionEntity
import io.ronesec.android.data.local.entity.OpenAttemptEntity
import io.ronesec.android.data.local.entity.TargetAppEntity
import io.ronesec.android.domain.model.AnimationType
import io.ronesec.android.domain.model.AttemptOutcome

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Converters {
    @TypeConverter
    fun fromAnimationType(value: AnimationType): String = value.name

    @TypeConverter
    fun toAnimationType(value: String): AnimationType = try {
        AnimationType.valueOf(value)
    } catch (e: Exception) {
        AnimationType.FILL
    }

    @TypeConverter
    fun fromAttemptOutcome(value: AttemptOutcome): String = value.name

    @TypeConverter
    fun toAttemptOutcome(value: String): AttemptOutcome = try {
        AttemptOutcome.valueOf(value)
    } catch (e: Exception) {
        AttemptOutcome.ABANDONED
    }
}

@Database(
    entities = [
        TargetAppEntity::class,
        OpenAttemptEntity::class,
        AccessGrantEntity::class,
        BlockSessionEntity::class,
        BlockScheduleEntity::class,
        AppSettingEntity::class
    ],
    version = 3,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun targetAppDao(): TargetAppDao
    abstract fun openAttemptDao(): OpenAttemptDao
    abstract fun accessGrantDao(): AccessGrantDao
    abstract fun blockDao(): BlockDao
    abstract fun settingsDao(): SettingsDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_open_attempts_packageName_timestamp` ON `open_attempts` (`packageName`, `timestamp`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_open_attempts_timestamp_outcome` ON `open_attempts` (`timestamp`, `outcome`)")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "ronesec.db"
                ).addMigrations(MIGRATION_2_3)
                .fallbackToDestructiveMigration()
                .build().also { INSTANCE = it }
            }
        }
    }
}
