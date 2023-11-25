package com.invincible.jedishare.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query

@Dao
interface HistoryItemDao {
    @Query("SELECT * FROM HistoryItem")
    fun getAll(): List<HistoryItem>

    @Insert
    fun insertAll(vararg historyItems: HistoryItem)

    @Delete
    fun delete(historyItem: HistoryItem)
}