package com.example.paceface

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.paceface.databinding.UserRegistrationConfirmationScreenBinding

class UserRegistrationConfirmationScreenActivity : AppCompatActivity() {

    private lateinit var binding: UserRegistrationConfirmationScreenBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = UserRegistrationConfirmationScreenBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 「戻る」ボタンがクリックされた時の処理
        binding.btnBack.setOnClickListener {
            // 現在の画面を終了して、前の画面（ユーザー登録画面）に戻る
            finish()
        }

        // 「登録」ボタンがクリックされた時の処理
        binding.btnRegister.setOnClickListener {
            // UserRegistrationCompleteScreenActivity へ画面遷移
            val intent = Intent(this, UserRegistrationCompleteScreenActivity::class.java)
            startActivity(intent)
        }
    }
}
