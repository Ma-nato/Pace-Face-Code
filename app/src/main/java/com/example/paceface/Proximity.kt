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
        ),
        ForeignKey(
            entity = Badge::class,
            parentColumns = ["badgeId"],
            childColumns = ["badgeId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = Emotion::class,
            parentColumns = ["emotionId"],
            childColumns = ["emotionId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Emotion::class,
            parentColumns = ["emotionId"],
            childColumns = ["passedUserEmotionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["userId"]),
        Index(value = ["passedUserId"]),
        Index(value = ["badgeId"]),
        Index(value = ["emotionId"]),
        Index(value = ["passedUserEmotionId"])
    ]
)
data class Proximity(
    @PrimaryKey(autoGenerate = true)
    val proximityId: Int = 0,
    val userId: Int,
    val passedUserId: Int,
    val timestamp: Long,
    val isConfirmed: Boolean,
    val badgeId: Int?,
    val emotionId: Int,
    val passedUserEmotionId: Int
)
