package com.example.paceface

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "Proximity",
    foreignKeys = [
        ForeignKey(
            entity = User::class,
            parentColumns = ["userId"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = User::class,
            parentColumns = ["userId"],
            childColumns = ["passedUserId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["userId"]), Index(value = ["passedUserId"])]
)
data class Proximity(
    @PrimaryKey(autoGenerate = true)
    val proximityId: Int = 0,
    val userId: Int,
    val passedUserId: Int,
    val timestamp: Long,
    val isConfirmed: Boolean
)
