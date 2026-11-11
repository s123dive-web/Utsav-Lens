package com.utsavlens.data

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.utsavlens.data.db.AppDatabase
import com.utsavlens.data.db.HistoryEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Migration scaffold from version 1. When the schema changes: bump AppDatabase.VERSION, add the
 * Migration to AppDatabase.MIGRATIONS, commit the new exported schema and add a
 * `migrateNtoM` test below that inserts old-shape rows and checks them after migration.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val dbName = "migration-test.db"

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    @Test
    fun version1SchemaOpensWithAllMigrationsAndKeepsData() {
        helper.createDatabase(dbName, 1).use { db ->
            db.execSQL(
                "INSERT INTO history (source_uri, theme_id, engine_id, options_json, output_uri, media_type, batch_id, created_at) " +
                    "VALUES ('content://src/1', 'diwali', 'lite', '{}', 'content://out/1', 'PHOTO', 'b1', 1000)",
            )
        }
        // Runs every registered migration up to the current version and validates the schema.
        helper.runMigrationsAndValidate(dbName, AppDatabase.VERSION, true, *AppDatabase.MIGRATIONS).close()

        val room = Room.databaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java, dbName)
            .addMigrations(*AppDatabase.MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        try {
            val rows = runBlocking { room.historyDao().observeAll().first() }
            assertThat(rows.map(HistoryEntity::sourceUri)).containsExactly("content://src/1")
        } finally {
            room.close()
        }
    }

    @Test
    fun everyVersionStepHasAMigration() {
        val covered = AppDatabase.MIGRATIONS.map { it.startVersion to it.endVersion }.toSet()
        for (v in 1 until AppDatabase.VERSION) {
            assertThat(covered).contains(v to v + 1)
        }
    }
}
