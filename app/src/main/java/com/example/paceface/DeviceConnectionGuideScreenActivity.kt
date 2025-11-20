package com.example.paceface

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import com.example.paceface.databinding.DeviceConnectionGuideScreenBinding

class DeviceConnectionGuideScreenActivity : AppCompatActivity() {

    private lateinit var binding: DeviceConnectionGuideScreenBinding

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
    }
}
