//BluetoothService.kt
package com.example.paceface

import android.annotation.SuppressLint
import android.app.Service
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.os.ParcelUuid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.util.UUID

class BluetoothService : Service() {

    private var piSocket: BluetoothSocket? = null
    private var piOutputStream: OutputStream? = null
    private val binder = BluetoothBinder()
    private val serviceScope = CoroutineScope(Dispatchers.IO)

    private val SERVICE_UUID = UUID.fromString("0000180D-0000-1000-8000-00805f9b34fb") // 心拍数サービスを模倣（例）
    private var currentUserId: Int = -1
    private var currentEmotionId: Int = 1
    private lateinit var appDatabase: AppDatabase
    private lateinit var badgeRepository: BadgeRepository

    inner class BluetoothBinder : Binder() {
        fun getService(): BluetoothService = this@BluetoothService
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onCreate() {
        super.onCreate()
        appDatabase = AppDatabase.getDatabase(this)
        badgeRepository = BadgeRepository(this)
    }

    @SuppressLint("MissingPermission")
    fun startProximityFeature(userId: Int) {
        this.currentUserId = userId
        startAdvertising()
        startScanning()
    }

    @SuppressLint("MissingPermission")
    private fun stopAdvertising() {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val advertiser = bluetoothManager.adapter.bluetoothLeAdvertiser
        advertiser?.stopAdvertising(object : AdvertiseCallback() {})
    }

    @SuppressLint("MissingPermission")
    private fun startAdvertising() {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val advertiser = bluetoothManager.adapter.bluetoothLeAdvertiser ?: return

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_POWER)
            .setConnectable(false)
            .setTimeout(0)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .build()

        // ManufacturerDataにUserId(4bytes)とEmotionId(1byte)を含める
        val manufacturerData = java.nio.ByteBuffer.allocate(5)
            .putInt(currentUserId)
            .put(currentEmotionId.toByte())
            .array()

        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .addServiceUuid(ParcelUuid(SERVICE_UUID))
            .addManufacturerData(0xFFFF, manufacturerData)
            .build()

        advertiser.startAdvertising(settings, data, object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                Log.d("BluetoothService", "Advertising started successfully")
            }
            override fun onStartFailure(errorCode: Int) {
                Log.e("BluetoothService", "Advertising failed: $errorCode")
            }
        })
    }

    @SuppressLint("MissingPermission")
    private fun startScanning() {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val scanner = bluetoothManager.adapter.bluetoothLeScanner ?: return

        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(SERVICE_UUID))
            .build()

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
            .build()

        scanner.startScan(listOf(filter), settings, object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val record = result.scanRecord ?: return
                val manufacturerData = record.getManufacturerSpecificData(0xFFFF) ?: return

                if (manufacturerData.size >= 5) {
                    val buffer = java.nio.ByteBuffer.wrap(manufacturerData)
                    val passedUserId = buffer.int
                    val passedUserEmotionId = buffer.get().toInt()

                    if (passedUserId != currentUserId) {
                        handleProximity(passedUserId, passedUserEmotionId)
                    }
                }
            }
        })
    }

    private val lastProximityMap = mutableMapOf<Int, Long>()
    private val PROXIMITY_COOLDOWN = 10 * 60 * 1000L // 10分間は同一ユーザーとのすれ違いを記録しない

    private fun handleProximity(passedUserId: Int, passedUserEmotionId: Int) {
        val now = System.currentTimeMillis()
        val lastTime = lastProximityMap[passedUserId] ?: 0L

        if (now - lastTime > PROXIMITY_COOLDOWN) {
            lastProximityMap[passedUserId] = now
            serviceScope.launch {
                val proximity = Proximity(
                    userId = currentUserId,
                    passedUserId = passedUserId,
                    timestamp = now,
                    isConfirmed = false,
                    badgeId = null,
                    emotionId = currentEmotionId,
                    passedUserEmotionId = passedUserEmotionId
                )
                appDatabase.proximityDao().insert(proximity)
                badgeRepository.checkAndAwardBadges(currentUserId, passedUserId)
                Log.i("BluetoothService", "Recorded proximity with user $passedUserId and checked badges")
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun connectToRaspberryPi() {
        if (piSocket?.isConnected == true) {
            Log.d("BluetoothService", "Already connected")
            return
        }

        serviceScope.launch {
            val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            val bluetoothAdapter = bluetoothManager.adapter
            val device = bluetoothAdapter.bondedDevices.firstOrNull {
                it.name == "raspberrypi"
            } ?: run {
                Log.e("BluetoothService", "Raspberry Pi not found")
                return@launch
            }

            try {
                Log.d("BluetoothService", "Connecting to Raspberry Pi...")
                val method = device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                val socket = method.invoke(device, 1) as BluetoothSocket
                bluetoothAdapter.cancelDiscovery()
                socket.connect() // This is a blocking call, now on IO dispatcher
                piSocket = socket
                piOutputStream = socket.outputStream
                Log.d("BluetoothService", "Connected successfully")
                // 【追加】接続直後に現在の表情を送信して、ラズパイ側の表示を更新する
                val emojiPrefs = getSharedPreferences("EmojiPrefs", Context.MODE_PRIVATE)
                val isAutoChangeEnabled = emojiPrefs.getBoolean("autoChangeEnabled", false)
                val initialEmotion = if (isAutoChangeEnabled) {
                    // 自動更新時は「睡眠(7)」または前回の表情
                    emojiPrefs.getString("lastDisplayedEmotion", "7")?.toInt() ?: 7
                } else {
                    // 固定時は選択中の表情
                    emojiPrefs.getString("selectedEmojiTag", "1")?.toInt() ?: 1
                }
                sendEmotion(initialEmotion)
            } catch (e: Exception) {
                Log.e("BluetoothService", "Connection failed", e)
                piSocket = null
                piOutputStream = null
            }
        }
    }

    fun sendEmotion(emotionId: Int) {
        this.currentEmotionId = emotionId
        // 表情が変わったのでアドバタイズを更新
        if (currentUserId != -1) {
            serviceScope.launch {
                stopAdvertising()
                delay(500)
                startAdvertising()
            }
        }
        serviceScope.launch {
            if (piOutputStream == null) {
                Log.e("BluetoothService", "Cannot send: piOutputStream is null. Attempting to reconnect...")
                connectToRaspberryPi()
                return@launch
            }
            try {
                piOutputStream?.write("$emotionId\n".toByteArray())
                piOutputStream?.flush()
                Log.d("BluetoothService", "Sent emotion: $emotionId")
            } catch (e: Exception) {
                Log.e("BluetoothService", "Send failed", e)
                piSocket = null
                piOutputStream = null
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            piSocket?.close()
        } catch (e: Exception) {
            Log.e("BluetoothService", "Close failed", e)
        }
    }
}