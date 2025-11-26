package com.example.paceface

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "DeviceStatus",
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
            childColumns = ["currentEmotionId"], // Corrected from emotionId
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["userId"]), Index(value = ["currentEmotionId"])] // Corrected from emotionId
)
data class DeviceStatus(
    @PrimaryKey(autoGenerate = true)
    val statusId: Int = 0,
    val userId: Int,
    val isConnected: Boolean,
    val batteryLevel: Int,
    val currentSpeed: Float,
    val currentEmotionId: Int
)
