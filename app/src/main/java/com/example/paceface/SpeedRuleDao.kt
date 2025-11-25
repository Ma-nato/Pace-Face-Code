package com.example.paceface

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SpeedRuleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(speedRule: SpeedRule)

    @Query("SELECT * FROM SpeedRule WHERE userId = :userId")
    suspend fun getSpeedRulesForUser(userId: Int): List<SpeedRule>
}
