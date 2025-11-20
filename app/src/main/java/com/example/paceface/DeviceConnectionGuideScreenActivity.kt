package com.example.paceface

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.paceface.databinding.DeviceConnectionGuideScreenBinding

class DeviceConnectionGuideScreenActivity : AppCompatActivity() {

    private lateinit var binding: DeviceConnectionGuideScreenBinding
    private val bluetoothAdapter: BluetoothAdapter? by lazy(LazyThreadSafetyMode.NONE) {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                checkBluetoothDevice()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DeviceConnectionGuideScreenBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 「設定へ」ボタンがクリックされた時の処理
        binding.btnToSettings.setOnClickListener {
            // Bluetooth設定画面を開くためのIntentを作成
            val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
            startActivity(intent)
        }

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            checkBluetoothDevice()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
        }
    }

    @SuppressLint("MissingPermission")
    private fun checkBluetoothDevice() {
        // 接続済みのデバイスを確認
        bluetoothAdapter?.bondedDevices?.forEach { device ->
            // デバイス名が "raspberrypi" だったらホーム画面へ遷移
            //if (device.name == "raspberrypi") {
            if (true) { // TODO: "raspberrypi"という名前のデバイスでのみ遷移するように、テスト後にこの条件を元に戻してください
                val intent = Intent(this, HomeScreenActivity::class.java)
                startActivity(intent)
                finish() // この画面は閉じる
                return // チェックを終了
            }
        }
    }
}
