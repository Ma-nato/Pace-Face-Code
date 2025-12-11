package com.example.paceface

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import java.security.MessageDigest

@Entity(
    tableName = "users",
    indices = [
        Index(value = ["email"], unique = true),
        Index(value = ["name"], unique = true) // nameもuniqueに設定
    ]
)
data class User(
    @PrimaryKey(autoGenerate = true)
    val userId: Int = 0,
    val email: String,
    val name: String,
    val password: String, // Hashed password
    val isEmailVerified: Boolean = false
) {
    companion object {
        fun hashPassword(password: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
            val hashBytes = digest.digest(password.toByteArray())
            return hashBytes.joinToString("") { "%02x".format(it) }
        }
    }
}

@Dao
interface SpeedRuleDao {
    @Insert(onConflict = OnConflictStrategy.Companion.REPLACE)
    suspend fun insert(speedRule: SpeedRule)

    @Insert(onConflict = OnConflictStrategy.Companion.REPLACE)
    suspend fun insertAll(speedRules: List<SpeedRule>)

    @Query("SELECT * FROM SpeedRule WHERE userId = :userId")
    suspend fun getSpeedRulesForUser(userId: Int): List<SpeedRule>

    @Query("DELETE FROM SpeedRule WHERE userId = :userId")
    suspend fun deleteRulesForUser(userId: Int)

    @Query("SELECT * FROM SpeedRule WHERE userId = :userId AND :speed >= minSpeed AND :speed < maxSpeed ORDER BY minSpeed DESC LIMIT 1")
    suspend fun getSpeedRuleForSpeed(userId: Int, speed: Float): SpeedRule?
}