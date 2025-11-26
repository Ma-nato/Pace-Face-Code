package com.example.paceface

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "achievements")
data class Achievement(
    @PrimaryKey(autoGenerate = true)
    val achievementId: Int = 0,
    val name: String,
    val description: String,
    val imageUrl: String
)
