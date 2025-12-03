package com.example.paceface

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface HistoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(history: History)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(histories: List<History>)

    @Query("SELECT * FROM History WHERE userId = :userId AND timestamp BETWEEN :startOfDay AND :endOfDay ORDER BY timestamp ASC")
    suspend fun getHistoryForUserOnDate(userId: Int, startOfDay: Long, endOfDay: Long): List<History>

    // --- Methods for Custom Rule Generation ---
    @Query("SELECT MIN(timestamp) FROM History WHERE userId = :userId")
    suspend fun getFirstTimestamp(userId: Int): Long?

    @Query("SELECT MAX(timestamp) FROM History WHERE userId = :userId")
    suspend fun getLastTimestamp(userId: Int): Long?

    @Query("SELECT walkingSpeed FROM History WHERE userId = :userId")
    suspend fun getAllWalkingSpeeds(userId: Int): List<Float>

    // --- Method for Dummy Data Deletion ---

    @Query("DELETE FROM History WHERE userId = :userId AND timestamp BETWEEN :startOfDay AND :endOfDay")
    suspend fun deleteHistoryForUserOnDate(userId: Int, startOfDay: Long, endOfDay: Long)

    // --- Transactional method for replacing data ---
    @Transaction
    suspend fun replaceDataForDate(userId: Int, startOfDay: Long, endOfDay: Long, histories: List<History>) {
        deleteHistoryForUserOnDate(userId, startOfDay, endOfDay)
        insertAll(histories)
    }
}
