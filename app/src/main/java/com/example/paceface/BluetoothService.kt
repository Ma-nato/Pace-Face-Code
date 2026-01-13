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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.OutputStream

class BluetoothService : Service() {

    private var piSocket: BluetoothSocket? = null
    private var piOutputStream: OutputStream? = null
    private val binder = BluetoothBinder()
    private val serviceScope = CoroutineScope(Dispatchers.IO)

    inner class BluetoothBinder : Binder() {
        fun getService(): BluetoothService = this@BluetoothService
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
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
            } catch (e: Exception) {
                Log.e("BluetoothService", "Connection failed", e)
                piSocket = null
                piOutputStream = null
            }
        }
    }

    fun sendEmotion(emotionId: Int) {
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