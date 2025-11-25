package com.example.paceface

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "DailyAverageSpeed",
    foreignKeys = [
        ForeignKey(
            entity = User::class,
            parentColumns = ["userId"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["userId"])]
)
data class DailyAverageSpeed(
    @PrimaryKey(autoGenerate = true)
    val dailySpeedId: Int = 0,
    val userId: Int,
    val date: Long,
    val averageSpeed: Float
)
