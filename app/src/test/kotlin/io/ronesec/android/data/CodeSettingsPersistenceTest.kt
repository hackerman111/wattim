package io.ronesec.android.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import io.ronesec.domain.model.TargetConfig
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class CodeSettingsPersistenceTest {
    @Test
    fun independentModesRoundTripThroughRoom() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = WattimDatabase.createInMemory(context)
        try {
            for (twoStage in listOf(false, true)) {
                for (emergency in listOf(false, true)) {
                    val config = TargetConfig(
                        packageName = "sample.app", displayName = "Sample",
                        twoStageUnlock = twoStage, unlockCodeLength = 10,
                        requireEmergencyCode = emergency
                    )
                    database.targetAppDao().insertOrUpdate(PolicyCompiler.toTargetEntity(config))
                    val saved = database.targetAppDao().getTarget(config.packageName)!!
                    assertEquals(config, PolicyCompiler.toTargetConfig(saved))
                }
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun migrationPreservesExistingTargetAndUsesDisabledDefaults() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "code-settings-migration.db"
        context.deleteDatabase(name)
        val schemaFile = listOf(
            File("schemas/io.ronesec.android.data.WattimDatabase/2.json"),
            File("app/schemas/io.ronesec.android.data.WattimDatabase/2.json")
        ).first { it.exists() }
        val schema = JSONObject(schemaFile.readText()).getJSONObject("database")
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(name)
                .callback(object : SupportSQLiteOpenHelper.Callback(2) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        val entities = schema.getJSONArray("entities")
                        for (index in 0 until entities.length()) {
                            val entity = entities.getJSONObject(index)
                            db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
                            val indices = entity.getJSONArray("indices")
                            for (i in 0 until indices.length()) {
                                db.execSQL(indices.getJSONObject(i).getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
                            }
                        }
                    }
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                }).build()
        )
        helper.writableDatabase.execSQL(
            "INSERT INTO target_apps VALUES ('sample.app', 'Sample', 1, 'Breathe', 'FILL', 8000, 300000, 0, 0, 20, 3600000, 7)"
        )
        helper.close()
        val database = Room.databaseBuilder(context, WattimDatabase::class.java, name)
            .addMigrations(WattimDatabase.MIGRATION_2_3, WattimDatabase.MIGRATION_3_4, WattimDatabase.MIGRATION_4_5).allowMainThreadQueries().build()
        try {
            val target = database.targetAppDao().getTarget("sample.app")!!
            assertEquals("Sample", target.displayName)
            assertEquals("Breathe", target.phrase)
            assertEquals(7L, target.rowVersion)
            assertFalse(target.twoStageUnlock)
            assertFalse(target.requireEmergencyCode)
            assertEquals(4, target.unlockCodeLength)
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }
}
