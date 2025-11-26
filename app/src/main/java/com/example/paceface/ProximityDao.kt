package com.example.paceface

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ProximityDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(proximity: Proximity)

    @Query("SELECT * FROM Proximity WHERE userId = :userId ORDER BY timestamp DESC")
    suspend fun getProximityHistoryForUser(userId: Int): List<Proximity>

    @Query("""
        SELECT p.proximityId, p.passedUserId, u.name as passedUserName, p.timestamp, p.isConfirmed, p.badgeId, p.passedUserEmotionId
        FROM Proximity p
        INNER JOIN users u ON p.passedUserId = u.userId
        WHERE p.userId = :userId
        ORDER BY p.timestamp DESC
    """)
    suspend fun getProximityHistoryWithUser(userId: Int): List<ProximityHistoryItem>

}

data class ProximityHistoryItem(
    val proximityId: Int,
    val passedUserId: Int,
    val passedUserName: String,
    val timestamp: Long,
    val isConfirmed: Boolean,
    val badgeId: Int?,
    val passedUserEmotionId: Int
)
