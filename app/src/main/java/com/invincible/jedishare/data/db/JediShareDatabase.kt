package com.invincible.jedishare.data.db

import timber.log.Timber

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import android.content.Context

/**
 * Room database for Just Share.
 *
 * Currently contains the [TransferHistoryEntity] table.
 * Increment version and add migrations when adding new tables / modifying columns.
 */
@Database(
    entities = [TransferHistoryEntity::class],
    version = 3,
    exportSchema = true  // enables migration testing with MigrationTestHelper
)
abstract class JediShareDatabase : RoomDatabase() {
    abstract fun transferHistoryDao(): TransferHistoryDao

    companion object {
        const val DATABASE_NAME = "jedishare_db"

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE transfer_history ADD COLUMN is_altersend INTEGER NOT NULL DEFAULT 0"
                )
            }
        }
    }
}
