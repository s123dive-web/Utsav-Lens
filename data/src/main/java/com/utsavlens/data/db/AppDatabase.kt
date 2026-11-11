package com.utsavlens.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration

@Database(
    entities = [HistoryEntity::class, JobEntity::class, CloudUsageEntity::class, ModelFileEntity::class],
    version = AppDatabase.VERSION,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao
    abstract fun jobDao(): JobDao
    abstract fun cloudUsageDao(): CloudUsageDao
    abstract fun modelFileDao(): ModelFileDao

    companion object {
        const val VERSION = 1
        const val NAME = "utsavlens.db"

        /**
         * Every schema change adds a Migration here AND a case in MigrationTest. Destructive
         * fallback is deliberately not enabled: user history must survive upgrades.
         */
        val MIGRATIONS: Array<Migration> = arrayOf()

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, NAME)
                .addMigrations(*MIGRATIONS)
                .build()
    }
}
