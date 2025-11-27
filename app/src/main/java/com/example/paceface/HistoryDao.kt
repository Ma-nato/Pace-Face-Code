package com.example.paceface

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface HistoryDao {

    @Insert
    suspend fun insert(history: History)

    @Query("SELECT * FROM history WHERE userId = :userId AND timestamp >= :startOfDay AND timestamp < :endOfDay")
    suspend fun getHistoryForUserOnDate(userId: Int, startOfDay: Long, endOfDay: Long): List<History>

    @Query("DELETE FROM history WHERE userId = :userId AND timestamp >= :startOfDay AND timestamp < :endOfDay")
    suspend fun deleteHistoryForUserOnDate(userId: Int, startOfDay: Long, endOfDay: Long)
}
