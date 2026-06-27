package com.example.data

import kotlinx.coroutines.flow.Flow

class HistoryRepository(private val historyDao: HistoryDao) {
    val allHistory: Flow<List<HistoryEntity>> = historyDao.getAllHistory()

    suspend fun insert(entity: HistoryEntity): Long {
        return historyDao.insertHistory(entity)
    }

    suspend fun updateStatus(id: Int, status: String) {
        historyDao.updateHistoryStatus(id, status)
    }

    suspend fun delete(id: Int) {
        historyDao.deleteHistoryItem(id)
    }

    suspend fun clearAll() {
        historyDao.clearHistory()
    }
}
