//LocationTrackingService.kt
package com.example.paceface

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.gms.location.*
import kotlinx.coroutines.*
import java.util.Calendar

class LocationTrackingService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var appDatabase: AppDatabase
    private var currentUserId: Int = -1

    private val serviceScope = CoroutineScope(Dispatchers.IO)
    private val speedReadings = mutableListOf<Float>()

    companion object {
        const val ACTION_START = "com.example.paceface.action.START_LOCATION_TRACKING"
        const val ACTION_STOP = "com.example.paceface.action.STOP_LOCATION_TRACKING"
        const val EXTRA_USER_ID = "com.example.paceface.extra.USER_ID"
        const val NOTIFICATION_CHANNEL_ID = "LocationTrackingChannel_v3"
        const val NOTIFICATION_ID = 1
        const val BROADCAST_SPEED_UPDATE = "com.example.paceface.broadcast.SPEED_UPDATE"
        const val EXTRA_SPEED = "com.example.paceface.extra.SPEED"
    }

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        appDatabase = AppDatabase.getDatabase(this)
        createLocationCallback()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                currentUserId = intent.getIntExtra(EXTRA_USER_ID, -1)
                if (currentUserId != -1) {
                    startForegroundService()
                    startLocationUpdates()
                    startMinuteTickLoop()
                }
            }
            ACTION_STOP -> {
                fusedLocationClient.removeLocationUpdates(locationCallback)
                serviceScope.launch {
                    saveMinuteAverageSpeedToDb()
                    withContext(Dispatchers.Main) {
                        val stopIntent = Intent(BROADCAST_SPEED_UPDATE)
                        stopIntent.putExtra(EXTRA_SPEED, 0.0f)
                        LocalBroadcastManager.getInstance(this@LocationTrackingService).sendBroadcast(stopIntent)
                        stopSelf()
                    }
                }
            }
        }
        return START_STICKY
    }

    private fun startForegroundService() {
        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("PaceFace")
            .setContentText("速度を記録中です...")
            .setSmallIcon(R.drawable.ic_splash_logo) // Replace with your app icon
            .setSilent(true)
            .build()
        startForeground(NOTIFICATION_ID, notification)
    }

    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000)
            .build()

        try {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
        } catch (e: SecurityException) {
            // Handle exception
        }
    }

    private fun updateNotification(speedKmh: Float) {
        val statusText = "現在の速度: ${String.format("%.1f", speedKmh)} km/h"
        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("PaceFace が動作中")
            .setContentText(statusText)
            .setSmallIcon(R.drawable.ic_splash_logo)
            .setOngoing(true)
            .setSilent(true)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun createLocationCallback() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    val speedKmh = getWalkingSpeed(location)
                    if (speedKmh >= 0) {
                        updateNotification(speedKmh)

                        // 速度リストに追加（平均計算用）
                        synchronized(speedReadings) {
                            speedReadings.add(speedKmh)
                        }

                        // リアルタイム速度をブロードキャスト送信
                        val intent = Intent(BROADCAST_SPEED_UPDATE)
                        intent.putExtra(EXTRA_SPEED, speedKmh)
                        LocalBroadcastManager.getInstance(this@LocationTrackingService).sendBroadcast(intent)
                    }
                }
            }
        }
    }

    private fun getWalkingSpeed(location: Location): Float {
        // Android標準の速度情報 (location.speed) が利用できるかチェック
        if (location.hasSpeed()) {
            val speedKmh = location.speed * 3.6f

            // 異常値を除外（歩行速度として妥当な0〜15km/hの範囲に限定）
            if (speedKmh in 0f..15f) {
                // 静止状態に近い微小な値は0.0fとして扱う
                if (speedKmh < 0.5f) {
                    return 0.0f
                }
                return speedKmh
            }
        }

        // 速度が取得できない、または範囲外の場合は無効な値として-1fを返す
        return -1f
    }

    private fun startMinuteTickLoop() {
        serviceScope.launch {
            // サービス起動後、最初の「次の分の始まり」まで待つ
            val now = Calendar.getInstance()
            val seconds = now.get(Calendar.SECOND)
            val initialDelayMillis = (60 - seconds) * 1000L

            // 最初のディレイ
            delay(initialDelayMillis)

            // 次の分の始まりで最初の保存を実行
            saveMinuteAverageSpeedToDb()

            // 以降、60秒（1分）ごとに保存処理を実行するループ
            while (isActive) {
                // 実行タイミングがズレないように、固定で60秒待機する
                delay(60000L)
                saveMinuteAverageSpeedToDb()
            }
        }
    }

    private fun saveMinuteAverageSpeedToDb() {
        if (speedReadings.isEmpty() || currentUserId == -1) return

        val readingsToSave: List<Float>
        synchronized(speedReadings) {
            readingsToSave = ArrayList(speedReadings)
            speedReadings.clear()
        }

        if (readingsToSave.isNotEmpty()) {
            val averageSpeed = readingsToSave.average().toFloat()

            serviceScope.launch {
                val timestampCal = Calendar.getInstance()
                timestampCal.add(Calendar.MINUTE, -1)
                timestampCal.set(Calendar.SECOND, 0)
                timestampCal.set(Calendar.MILLISECOND, 0)
                val timestamp = timestampCal.timeInMillis

                val speedRule = appDatabase.speedRuleDao().getSpeedRuleForSpeed(currentUserId, averageSpeed)
                val emotionId = speedRule?.emotionId ?: 1 // Default to Normal

                val newHistory = History(
                    userId = currentUserId,
                    timestamp = timestamp,
                    walkingSpeed = averageSpeed,
                    emotionId = emotionId
                )
                appDatabase.historyDao().insert(newHistory)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Location Tracking",
                NotificationManager.IMPORTANCE_LOW
            )
            serviceChannel.vibrationPattern = longArrayOf(0L)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }
}