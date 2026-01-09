//DeviceStatusDao.kt
package com.example.paceface

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DeviceStatusDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(deviceStatus: DeviceStatus)

    @Query("SELECT * FROM DeviceStatus WHERE userId = :userId")
    suspend fun getDeviceStatusForUser(userId: Int): DeviceStatus?

    // 【修正点】UIのリアクティブな更新のためにFlowを返すメソッドを追加
    @Query("SELECT * FROM DeviceStatus WHERE userId = :userId LIMIT 1")
    fun getDeviceStatusForUserFlow(userId: Int): Flow<DeviceStatus?>
}