package com.utsavlens.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.utsavlens.data.db.AppDatabase
import com.utsavlens.data.db.HistoryEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Migration scaffold from version 1, driven by the exported schema files in data/schemas.
 *
 * It builds a database exactly as an old app version would have created it (from that version's
 * exported schema), then opens it with the current Room database and all registered migrations.
 * Room verifies the resulting schema against its identity hash on open, so a missing or wrong
 * migration fails here.
 *
 * When the schema changes: bump AppDatabase.VERSION, add the Migration to AppDatabase.MIGRATIONS,
 * commit the new exported schema file; [everyExportedVersionMigratesToCurrent] then covers it.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val schemaDir = File("schemas/${AppDatabase::class.java.canonicalName}")

    private fun createFromExportedSchema(dbName: String, version: Int) {
        val schema = Json.parseToJsonElement(File(schemaDir, "$version.json").readText()).jsonObject.getValue("database").jsonObject
        assertThat(schema.getValue("version").jsonPrimitive.content.toInt()).isEqualTo(version)
        val file = context.getDatabasePath(dbName)
        file.parentFile?.mkdirs()
        file.delete()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            for (entity in schema.getValue("entities").jsonArray) {
                val e = entity.jsonObject
                val table = e.getValue("tableName").jsonPrimitive.content
                db.execSQL(e.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table))
                e["indices"]?.jsonArray?.forEach { index ->
                    db.execSQL(index.jsonObject.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table))
                }
            }
            schema.getValue("setupQueries").jsonArray.forEach { db.execSQL(it.jsonPrimitive.content) }
            db.version = version
        }
    }

    private fun openCurrent(dbName: String): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(*AppDatabase.MIGRATIONS)
            .allowMainThreadQueries()
            .build()

    @Test
    fun version1DataSurvivesOpeningWithCurrentSchema() {
        val dbName = "migration-v1.db"
        createFromExportedSchema(dbName, 1)
        SQLiteDatabase.openDatabase(context.getDatabasePath(dbName).path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.execSQL(
                "INSERT INTO history (source_uri, theme_id, engine_id, options_json, output_uri, media_type, batch_id, created_at) " +
                    "VALUES ('content://src/1', 'diwali', 'lite', '{}', 'content://out/1', 'PHOTO', 'b1', 1000)",
            )
        }
        val room = openCurrent(dbName)
        try {
            val rows = runBlocking { room.historyDao().observeAll().first() }
            assertThat(rows.map(HistoryEntity::sourceUri)).containsExactly("content://src/1")
        } finally {
            room.close()
        }
    }

    @Test
    fun everyExportedVersionMigratesToCurrent() {
        val versions = schemaDir.listFiles { f -> f.name.endsWith(".json") }!!.map { it.name.removeSuffix(".json").toInt() }.sorted()
        assertWithMessage("exported schema for the current version must be committed").that(versions).contains(AppDatabase.VERSION)
        for (version in versions) {
            val dbName = "migration-from-$version.db"
            createFromExportedSchema(dbName, version)
            val room = openCurrent(dbName)
            try {
                // Forces Room to open the file: runs migrations and validates the schema hash.
                runBlocking { room.historyDao().count() }
            } finally {
                room.close()
            }
        }
    }

    @Test
    fun everyVersionStepHasAMigration() {
        val covered = AppDatabase.MIGRATIONS.map { it.startVersion to it.endVersion }.toSet()
        for (v in 1 until AppDatabase.VERSION) assertThat(covered).contains(v to v + 1)
    }
}
