package com.example.paceface

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface DeviceStatusDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(deviceStatus: DeviceStatus)

    @Query("SELECT * FROM DeviceStatus WHERE userId = :userId")
    suspend fun getDeviceStatusForUser(userId: Int): DeviceStatus?
}
