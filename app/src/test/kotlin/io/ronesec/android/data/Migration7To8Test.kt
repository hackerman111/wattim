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
class Migration7To8Test {

    @Test
    fun migrationPreservesExistingSettingsAndAddsDynamicSystemFilteringDefault() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "system-filtering-migration.db"
        context.deleteDatabase(name)
        val schemaFile = listOf(
            File("schemas/io.ronesec.android.data.WattimDatabase/7.json"),
            File("app/schemas/io.ronesec.android.data.WattimDatabase/7.json")
        ).first { it.exists() }
        val schema = JSONObject(schemaFile.readText()).getJSONObject("database")
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(name)
                .callback(object : SupportSQLiteOpenHelper.Callback(7) {
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
        // Schema 7 app_settings columns: 9 columns
        helper.writableDatabase.execSQL(
            "INSERT INTO app_settings VALUES (1, 'NORD', 'ru', 10, 1, 'NONE', NULL, 15, 3)"
        )
        helper.close()

        val database = Room.databaseBuilder(context, WattimDatabase::class.java, name)
            .addMigrations(WattimDatabase.MIGRATION_7_8).allowMainThreadQueries().build()
        try {
            val settings = database.appSettingsDao().getSettings()!!
            assertEquals("NORD", settings.themeId)
            assertEquals("ru", settings.language)
            assertEquals(10, settings.savedSessionMinutes)
            assertEquals(true, settings.showOverlayStats)
            assertEquals(15, settings.customEmergencyMinutes)
            assertEquals(3L, settings.rowVersion)
            assertTrue("dynamicSystemAppFiltering must default to true", settings.dynamicSystemAppFiltering)
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }
}
