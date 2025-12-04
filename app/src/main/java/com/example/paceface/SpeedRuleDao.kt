package com.example.paceface

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SpeedRuleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(speedRule: SpeedRule)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(speedRules: List<SpeedRule>)

    @Query("SELECT * FROM SpeedRule WHERE userId = :userId")
    suspend fun getSpeedRulesForUser(userId: Int): List<SpeedRule>

    @Query("DELETE FROM SpeedRule WHERE userId = :userId")
    suspend fun deleteRulesForUser(userId: Int)

    @Query("SELECT * FROM SpeedRule WHERE userId = :userId AND :speed >= minSpeed AND :speed < maxSpeed ORDER BY minSpeed DESC LIMIT 1")
    suspend fun getSpeedRuleForSpeed(userId: Int, speed: Float): SpeedRule?
}
