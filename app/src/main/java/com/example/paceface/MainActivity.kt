package com.example.paceface

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.example.paceface.databinding.SelectionScreenBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: SelectionScreenBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        // Handle the splash screen transition.
        installSplashScreen()

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
