package com.invincible.jedishare.data.db

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import android.content.Context

/**
 * Room database for JediShare.
 *
 * Currently contains the [TransferHistoryEntity] table.
 * Increment version and add migrations when adding new tables / modifying columns.
 */
@Database(
    entities = [TransferHistoryEntity::class],
    version = 1,
    exportSchema = false
)
abstract class JediShareDatabase : RoomDatabase() {
    abstract fun transferHistoryDao(): TransferHistoryDao

    companion object {
        const val DATABASE_NAME = "jedishare_db"
    }
}
