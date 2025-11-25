package com.example.paceface

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface BadgeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(badge: Badge)

    @Query("SELECT * FROM Badge")
    suspend fun getAllBadges(): List<Badge>
}
