package com.invincible.jedishare.data.repository

import com.invincible.jedishare.data.db.TransferHistoryDao
import com.invincible.jedishare.data.db.TransferHistoryEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Repository that abstracts the [TransferHistoryDao] for use by ViewModels.
 * Provides a clean API independent of the Room DAO.
 */
class TransferHistoryRepository @Inject constructor(
    private val dao: TransferHistoryDao
) {
    fun getAllHistory(): Flow<List<TransferHistoryEntity>> = dao.getAllHistory()
    fun getRecentHistory(limit: Int = 20): Flow<List<TransferHistoryEntity>> = dao.getRecentHistory(limit)
    suspend fun addEntry(entry: TransferHistoryEntity) = dao.insert(entry)
    suspend fun deleteEntry(entry: TransferHistoryEntity) = dao.delete(entry)
    suspend fun clearAll() = dao.clearAll()
}
