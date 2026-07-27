package com.invincible.jedishare.data.db

import timber.log.Timber

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * DAO for reading and writing transfer history records.
 */
@Dao
interface TransferHistoryDao {

    /** Inserts a new history entry, replacing on conflict. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: TransferHistoryEntity): Long

    /** Returns all history entries ordered by most recent first as a live Flow. */
    @Query("SELECT * FROM transfer_history ORDER BY timestamp_ms DESC")
    fun getAllHistory(): Flow<List<TransferHistoryEntity>>

    /** Returns the N most recent history entries. */
    @Query("SELECT * FROM transfer_history ORDER BY timestamp_ms DESC LIMIT :limit")
    fun getRecentHistory(limit: Int = 20): Flow<List<TransferHistoryEntity>>

    /** Deletes a specific history entry by ID. */
    @Delete
    suspend fun delete(entry: TransferHistoryEntity)

    /** Clears all history entries. */
    @Query("DELETE FROM transfer_history")
    suspend fun clearAll()
}
