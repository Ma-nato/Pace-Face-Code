package com.example.paceface

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface HourlyAverageSpeedDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(hourlyAverageSpeed: HourlyAverageSpeed)

    @Query("SELECT * FROM HourlyAverageSpeed WHERE userId = :userId AND timestamp = :timestamp")
    suspend fun getHourlyAverageSpeed(userId: Int, timestamp: Long): HourlyAverageSpeed?

    @Query("""
        SELECT * FROM HourlyAverageSpeed
        WHERE userId = :userId AND timestamp >= :startOfDay AND timestamp < :endOfDay
        ORDER BY timestamp ASC
        """)
    suspend fun getHourlyAverageSpeedForDate(userId: Int, startOfDay: Long, endOfDay: Long): List<HourlyAverageSpeed>
}
