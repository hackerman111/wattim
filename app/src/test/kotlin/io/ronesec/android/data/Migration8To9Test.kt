package io.ronesec.android.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class Migration8To9Test {

    @Test
    fun migrationPreservesExistingTargetAndDefaultsEmergencyAccessEnabledToTrue() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "emergency-access-migration.db"
        context.deleteDatabase(name)
        val schemaFile = listOf(
            File("schemas/io.ronesec.android.data.WattimDatabase/8.json"),
            File("app/schemas/io.ronesec.android.data.WattimDatabase/8.json")
        ).first { it.exists() }
        val schema = JSONObject(schemaFile.readText()).getJSONObject("database")
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(name)
                .callback(object : SupportSQLiteOpenHelper.Callback(8) {
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
        // Schema 8 columns: 26 columns
        helper.writableDatabase.execSQL(
            "INSERT INTO target_apps VALUES ('sample.app', 'Sample', 1, 'Breathe', 'FILL', 8000, 300000, 0, 0, 20, 3600000, 7, 1, 6, 1, 1, 12000, 1, 3, 0, 1, 1, 5, 6000, 0, 20)"
        )
        helper.close()

        val database = Room.databaseBuilder(context, WattimDatabase::class.java, name)
            .addMigrations(WattimDatabase.MIGRATION_8_9).allowMainThreadQueries().build()
        try {
            val target = database.targetAppDao().getTarget("sample.app")!!
            assertEquals("Sample", target.displayName)
            assertEquals("Breathe", target.phrase)
            assertEquals(7L, target.rowVersion)
            assertTrue("emergencyAccessEnabled must default to true", target.emergencyAccessEnabled)
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }
}
