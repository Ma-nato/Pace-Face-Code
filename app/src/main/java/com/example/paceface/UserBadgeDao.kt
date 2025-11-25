package com.example.paceface

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface UserBadgeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(userBadge: UserBadge)

    @Query("SELECT * FROM UserBadge WHERE userId = :userId")
    suspend fun getBadgesForUser(userId: Int): List<UserBadge>
}
