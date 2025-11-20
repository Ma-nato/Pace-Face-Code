package com.example.paceface

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface SpeedDataDao {
    @Insert
    suspend fun insert(speedData: SpeedData)

    @Query("SELECT * FROM speed_data WHERE timestamp BETWEEN :startTime AND :endTime")
    suspend fun getDataForDate(startTime: Long, endTime: Long): List<SpeedData>
}
