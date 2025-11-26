package com.example.paceface

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy

@Dao
interface EmotionDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(emotions: List<Emotion>)
}
