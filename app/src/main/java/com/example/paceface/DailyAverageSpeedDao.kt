package com.example.paceface

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface DailyAverageSpeedDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(dailyAverageSpeed: DailyAverageSpeed)

    @Query("SELECT * FROM DailyAverageSpeed WHERE userId = :userId AND date = :date")
    suspend fun getDailyAverageSpeed(userId: Int, date: Long): DailyAverageSpeed?
}
