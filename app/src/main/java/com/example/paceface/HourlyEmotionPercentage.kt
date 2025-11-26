package com.example.paceface

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "HourlyEmotionPercentage",
    foreignKeys = [
        ForeignKey(
            entity = User::class,
            parentColumns = ["userId"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Emotion::class,
            parentColumns = ["emotionId"],
            childColumns = ["emotionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["userId"]), Index(value = ["emotionId"])]
)
data class HourlyEmotionPercentage(
    @PrimaryKey(autoGenerate = true)
    val hourlyEmotionId: Int = 0,
    val userId: Int,
    val timestamp: Long,
    val emotionId: Int,
    val percentage: Float
)
