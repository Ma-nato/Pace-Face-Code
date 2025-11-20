package com.example.paceface

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.paceface.databinding.UserRegistrationCompleteScreenBinding

class UserRegistrationCompleteScreenActivity : AppCompatActivity() {

    private lateinit var binding: UserRegistrationCompleteScreenBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = UserRegistrationCompleteScreenBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 「OK」ボタンがクリックされた時の処理
        binding.btnOk.setOnClickListener {
            // デバイス接続案内画面へ画面遷移
            val intent = Intent(this, DeviceConnectionGuideScreenActivity::class.java)
            startActivity(intent)
        }
    }
}
