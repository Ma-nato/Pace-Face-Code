package com.example.paceface

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface HourlyEmotionPercentageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(hourlyEmotionPercentage: HourlyEmotionPercentage)

    @Query("SELECT * FROM HourlyEmotionPercentage WHERE userId = :userId AND timestamp = :timestamp")
    suspend fun getHourlyEmotionPercentage(userId: Int, timestamp: Long): List<HourlyEmotionPercentage>

    @Query("""
        SELECT * FROM HourlyEmotionPercentage
        WHERE userId = :userId AND timestamp >= :startOfDay AND timestamp < :endOfDay
        ORDER BY timestamp ASC, emotionId ASC
        """)
    suspend fun getHourlyEmotionPercentageForDate(userId: Int, startOfDay: Long, endOfDay: Long): List<HourlyEmotionPercentage>
}
