package com.example.paceface

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.paceface.databinding.SelectionScreenBinding

class SelectionScreenActivity : AppCompatActivity() {

    private lateinit var binding: SelectionScreenBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = SelectionScreenBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // ログインボタンのクリックリスナー
        binding.btnLogin.setOnClickListener {
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
        }

        // 新規登録ボタンのクリックリスナー
        binding.btnRegister.setOnClickListener {
            val intent = Intent(this, UserRegistrationScreenActivity::class.java)
            startActivity(intent)
        }
    }
}
