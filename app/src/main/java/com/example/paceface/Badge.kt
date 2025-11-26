package com.example.paceface

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "Badge")
data class Badge(
    @PrimaryKey(autoGenerate = true)
    val badgeId: Int = 0,
    val name: String,
    val description: String,
    val imageUrl: String
)
