package com.example.paceface

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "UserBadge",
    foreignKeys = [
        ForeignKey(
            entity = User::class,
            parentColumns = ["userId"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Badge::class,
            parentColumns = ["badgeId"],
            childColumns = ["badgeId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["userId"]), Index(value = ["badgeId"])]
)
data class UserBadge(
    @PrimaryKey(autoGenerate = true)
    val userBadgeId: Int = 0,
    val userId: Int,
    val badgeId: Int,
    val achievedAt: Long
)
