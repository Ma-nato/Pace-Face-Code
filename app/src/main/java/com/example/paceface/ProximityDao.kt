package com.example.paceface

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ProximityDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(proximity: Proximity)

    @Query("SELECT * FROM Proximity WHERE userId = :userId")
    suspend fun getProximityEventsForUser(userId: Int): List<Proximity>
}
