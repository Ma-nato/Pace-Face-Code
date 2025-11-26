package com.example.paceface

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface UserDao {
    // On conflict, abort the transaction and throw an exception.
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(user: User)

    @Query("SELECT * FROM users WHERE userId = :userId")
    suspend fun getUserById(userId: Int): User?

    @Query("SELECT * FROM users WHERE email = :email")
    suspend fun getUserByEmail(email: String): User?

    @Query("SELECT * FROM users WHERE name = :name")
    suspend fun getUserByName(name: String): User?

    @Update
    suspend fun update(user: User)
}
