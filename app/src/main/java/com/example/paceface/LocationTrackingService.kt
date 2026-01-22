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
import android.util.Log
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.SpeedRecord
import androidx.health.connect.client.units.Velocity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.gms.location.*
import kotlinx.coroutines.*
import java.time.Instant
import java.util.Calendar
import kotlin.math.sqrt

class LocationTrackingService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var appDatabase: AppDatabase
    private var currentUserId: Int = -1

    // BluetoothService 関連
    private var bluetoothService: BluetoothService? = null
    private var isBound = false
    private var lastSentEmotionId: Int? = null

    private val serviceScope = CoroutineScope(Dispatchers.IO)

    // 加速度センサー関連
    private lateinit var sensorManager: SensorManager
    private var linearAccelerometer: Sensor? = null
    private var isMoving = true
    private var lastMovementTimestamp: Long = 0
    private val MOVEMENT_THRESHOLD = 0.5f // 加速度の閾値（m/s^2）
    private val STATIONARY_DELAY_MS = 3000L // 静止と判断するまでの時間（3秒）

    // 1分間の平均速度計算用
    private val speedReadings = mutableListOf<Float>()

    private val sensorEventListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent?) {
            if (event?.sensor?.type == Sensor.TYPE_LINEAR_ACCELERATION) {
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]
                val magnitude = sqrt(x * x + y * y + z * z)

                if (magnitude > MOVEMENT_THRESHOLD) {
                    lastMovementTimestamp = System.currentTimeMillis()
                    isMoving = true
                } else {
                    // 閾値を下回ってから一定時間経過したら静止とみなす
                    if (System.currentTimeMillis() - lastMovementTimestamp > STATIONARY_DELAY_MS) {
                        isMoving = false
                    }
                }
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    private val connection = object : android.content.ServiceConnection {
        override fun onServiceConnected(name: android.content.ComponentName?, service: android.os.IBinder?) {
            val binder = service as BluetoothService.BluetoothBinder
            bluetoothService = binder.getService()
            isBound = true
            bluetoothService?.connectToRaspberryPi()
        }

        override fun onServiceDisconnected(name: android.content.ComponentName?) {
            bluetoothService = null
            isBound = false
        }
    }

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

        // 加速度センサーの初期化
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        linearAccelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        linearAccelerometer?.let {
            sensorManager.registerListener(sensorEventListener, it, SensorManager.SENSOR_DELAY_NORMAL)
        }

        // BluetoothService にバインド
        val intent = Intent(this, BluetoothService::class.java)
        bindService(intent, connection, Context.BIND_AUTO_CREATE)
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
                    // 停止直前に現在のデータを保存
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
            .setSmallIcon(R.drawable.ic_splash_logo)
            .setSilent(true)
            .build()
        startForeground(NOTIFICATION_ID, notification)
    }

    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000)
            .setMinUpdateIntervalMillis(500)
            .setWaitForAccurateLocation(true)
            .setGranularity(Granularity.GRANULARITY_FINE)
            .build()

        try {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
        } catch (e: SecurityException) {
            Log.e("LocationTrackingService", "Location permission missing or denied.", e)
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

                    // FLPの提供する速度をそのまま利用 (m/s)
                    var speedMps = if (location.hasSpeed()) location.speed else 0f

                    // 加速度センサーで静止と判定されている場合は、速度を強制的に0にする
                    if (!isMoving) {
                        speedMps = 0f
                    }

                    // km/h に変換
                    val speedKmh = speedMps * 3.6f

                    if (speedKmh >= 0) {
                        updateNotification(speedKmh)

                        // 速度リストに追加（1分間の平均計算用）
                        synchronized(speedReadings) {
                            speedReadings.add(speedKmh)
                        }

                        // リアルタイム速度をブロードキャスト送信
                        val intent = Intent(BROADCAST_SPEED_UPDATE)
                        intent.putExtra(EXTRA_SPEED, speedKmh)
                        LocalBroadcastManager.getInstance(this@LocationTrackingService).sendBroadcast(intent)

                        // 表情を送信
                        sendEmotionBasedOnSpeed(speedKmh)

                        // Health Connect への書き込み
                        writeSpeedToHealthConnect(speedMps)
                    }
                }
            }
        }
    }

    private fun writeSpeedToHealthConnect(speedMps: Float) {
        serviceScope.launch {
            try {
                val healthConnectClient = HealthConnectClient.getOrCreate(this@LocationTrackingService)
                val now = Instant.now()
                val speedRecord = SpeedRecord(
                    startTime = now,
                    startZoneOffset = null,
                    endTime = now.plusMillis(100),
                    endZoneOffset = null,
                    samples = listOf(
                        SpeedRecord.Sample(
                            time = now,
                            speed = Velocity.metersPerSecond(speedMps.toDouble())
                        )
                    )
                )
                healthConnectClient.insertRecords(listOf(speedRecord))
                Log.d("HealthConnect", "Speed data written to Health Connect: $speedMps m/s")
            } catch (e: Exception) {
                Log.e("HealthConnect", "Failed to write speed data to Health Connect", e)
            }
        }
    }

    private fun sendEmotionBasedOnSpeed(speed: Float) {
        serviceScope.launch {
            val emojiPrefs = getSharedPreferences("EmojiPrefs", Context.MODE_PRIVATE)
            val isAutoChangeEnabled = emojiPrefs.getBoolean("autoChangeEnabled", false)

            val emotionId = if (isAutoChangeEnabled) {
                val speedRule = appDatabase.speedRuleDao().getSpeedRuleForSpeed(currentUserId, speed)
                speedRule?.emotionId ?: 7 // Default to Sleep (7)
            } else {
                val savedTag = emojiPrefs.getString("selectedEmojiTag", "1") ?: "1"
                savedTag.toInt()
            }

            if (lastSentEmotionId != emotionId) {
                bluetoothService?.sendEmotion(emotionId)
                lastSentEmotionId = emotionId
            }
        }
    }

    private fun startMinuteTickLoop() {
        serviceScope.launch {
            val now = Calendar.getInstance()
            val seconds = now.get(Calendar.SECOND)
            val initialDelayMillis = (60 - seconds) * 1000L
            delay(initialDelayMillis)

            // 最初の保存を実行
            saveMinuteAverageSpeedToDb()

            while (isActive) {
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
                // 1分前のタイムスタンプとして保存
                timestampCal.add(Calendar.MINUTE, -1)
                timestampCal.set(Calendar.SECOND, 0)
                timestampCal.set(Calendar.MILLISECOND, 0)
                val timestamp = timestampCal.timeInMillis

                val speedRule = appDatabase.speedRuleDao().getSpeedRuleForSpeed(currentUserId, averageSpeed)
                val emotionId = speedRule?.emotionId ?: 7

                val newHistory = History(
                    userId = currentUserId,
                    timestamp = timestamp,
                    walkingSpeed = averageSpeed,
                    emotionId = emotionId
                )
                appDatabase.historyDao().insert(newHistory)
                Log.d("LocationTrackingService", "Saved average speed to DB: $averageSpeed km/h")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // センサーの登録解除
        if (::sensorManager.isInitialized) {
            sensorManager.unregisterListener(sensorEventListener)
        }

        if (isBound) {
            unbindService(connection)
            isBound = false
        }
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