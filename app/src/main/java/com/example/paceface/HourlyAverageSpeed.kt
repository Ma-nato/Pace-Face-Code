package com.example.paceface

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "HourlyAverageSpeed",
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
data class HourlyAverageSpeed(
    @PrimaryKey(autoGenerate = true)
    val hourlySpeedId: Int = 0,
    val userId: Int,
    val timestamp: Long,
    val averageSpeed: Float
)
