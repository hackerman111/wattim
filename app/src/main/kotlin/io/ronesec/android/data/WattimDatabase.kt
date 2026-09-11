package io.ronesec.android.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import io.ronesec.android.data.dao.AccessGrantDao
import io.ronesec.android.data.dao.AppSettingsDao
import io.ronesec.android.data.dao.BlockScheduleDao
import io.ronesec.android.data.dao.BlockSessionDao
import io.ronesec.android.data.dao.OpenAttemptDao
import io.ronesec.android.data.dao.PolicyRevisionDao
import io.ronesec.android.data.dao.StatisticsDao
import io.ronesec.android.data.dao.TargetAppDao
import io.ronesec.android.data.entity.AccessGrantEntity
import io.ronesec.android.data.entity.AppSettingsEntity
import io.ronesec.android.data.entity.BlockScheduleEntity
import io.ronesec.android.data.entity.BlockSessionEntity
import io.ronesec.android.data.entity.BlockSessionTargetCrossRef
import io.ronesec.android.data.entity.OpenAttemptEntity
import io.ronesec.android.data.entity.PolicyRevisionEntity
import io.ronesec.android.data.entity.ScheduleOverrideEntity
import io.ronesec.android.data.entity.ScheduleTargetCrossRef
import io.ronesec.android.data.entity.TargetAppEntity

@Database(
    entities = [
        TargetAppEntity::class,
        OpenAttemptEntity::class,
        AccessGrantEntity::class,
        BlockSessionEntity::class,
        BlockSessionTargetCrossRef::class,
        BlockScheduleEntity::class,
        ScheduleTargetCrossRef::class,
        ScheduleOverrideEntity::class,
        AppSettingsEntity::class,
        PolicyRevisionEntity::class
    ],
    version = 4,
    exportSchema = true
)
abstract class WattimDatabase : RoomDatabase() {

    abstract fun targetAppDao(): TargetAppDao
    abstract fun openAttemptDao(): OpenAttemptDao
    abstract fun accessGrantDao(): AccessGrantDao
    abstract fun blockSessionDao(): BlockSessionDao
    abstract fun blockScheduleDao(): BlockScheduleDao
    abstract fun appSettingsDao(): AppSettingsDao
    abstract fun policyRevisionDao(): PolicyRevisionDao
    abstract fun statisticsDao(): StatisticsDao

    companion object {
        const val DATABASE_NAME = "ronesec.db"

        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE app_settings ADD COLUMN customEmergencyMinutes INTEGER DEFAULT NULL")
            }
        }

        val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE target_apps ADD COLUMN twoStageUnlock INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE target_apps ADD COLUMN unlockCodeLength INTEGER NOT NULL DEFAULT 4")
                db.execSQL("ALTER TABLE target_apps ADD COLUMN requireEmergencyCode INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE target_apps ADD COLUMN randomDurationEnabled INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE target_apps ADD COLUMN randomMaxDurationMs INTEGER NOT NULL DEFAULT 8000")
            }
        }

        @Volatile
        private var INSTANCE: WattimDatabase? = null

        fun getInstance(context: Context): WattimDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    WattimDatabase::class.java,
                    DATABASE_NAME
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build().also { INSTANCE = it }
            }
        }

        fun createInMemory(context: Context): WattimDatabase {
            return Room.inMemoryDatabaseBuilder(
                context.applicationContext,
                WattimDatabase::class.java
            ).allowMainThreadQueries().build()
        }
    }
}
