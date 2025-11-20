package com.example.paceface

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "speed_data")
data class SpeedData(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val timestamp: Long,
    val speed: Float
)
