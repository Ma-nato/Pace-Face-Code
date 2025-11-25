package com.example.paceface

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "Emotion")
data class Emotion(
    @PrimaryKey
    val emotionId: Int,
    val name: String,
    val imageUrl: String,
    val description: String
)
