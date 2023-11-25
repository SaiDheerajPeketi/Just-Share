package com.invincible.jedishare.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class HistoryItem(
    @PrimaryKey val uid: String,
    @ColumnInfo(name = "file_name") val file_name: String,
    @ColumnInfo(name = "file_size") val file_size: String,
    @ColumnInfo(name = "file_timestamp") val file_timestamp: String
)
