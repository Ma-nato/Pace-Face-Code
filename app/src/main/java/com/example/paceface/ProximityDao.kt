//ProximityDao.kt
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

    @Query("SELECT COUNT(*) FROM Proximity WHERE userId = :userId")
    suspend fun getProximityCountForUser(userId: Int): Int

    @Query("SELECT COUNT(*) FROM Proximity WHERE userId = :userId AND STRFTIME('%H', timestamp / 1000, 'unixepoch') BETWEEN '20' AND '05'")
    suspend fun getNightProximityCountForUser(userId: Int): Int

    @Query("SELECT COUNT(*) FROM Proximity WHERE userId = :userId AND STRFTIME('%H', timestamp / 1000, 'unixepoch') BETWEEN '06' AND '10'")
    suspend fun getMorningProximityCountForUser(userId: Int): Int

    @Query("SELECT EXISTS(SELECT 1 FROM Proximity WHERE userId = :userId AND passedUserEmotionId = :emotionId)")
    suspend fun hasEncounteredEmotion(userId: Int, emotionId: Int): Boolean

    @Query("SELECT COUNT(*) FROM Proximity WHERE userId = :userId AND passedUserId = :passedUserId")
    suspend fun getProximityCountWithUser(userId: Int, passedUserId: Int): Int

    @Query("SELECT COUNT(*) FROM Proximity WHERE userId = :userId AND passedUserEmotionId = :emotionId")
    suspend fun getProximityCountWithEmotion(userId: Int, emotionId: Int): Int

    @Query("SELECT COUNT(DISTINCT passedUserId) FROM Proximity WHERE userId = :userId AND DATE(timestamp / 1000, 'unixepoch') = DATE('now', 'localtime')")
    suspend fun getTodaysUniqueProximityCount(userId: Int): Int

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
