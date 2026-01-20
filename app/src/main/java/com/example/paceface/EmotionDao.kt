//EmotionDao.kt
package com.example.paceface

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface EmotionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(emotions: List<Emotion>)

    // 【新規追加】感情IDに基づいてEmotionオブジェクトを取得する
    @Query("SELECT * FROM Emotion WHERE emotionId = :emotionId")
    suspend fun getEmotionById(emotionId: Int): Emotion? // Emotion.kt に対応
}