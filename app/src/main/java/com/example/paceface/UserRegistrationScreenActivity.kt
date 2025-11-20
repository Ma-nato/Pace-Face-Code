package com.example.paceface

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.paceface.databinding.UserRegistrationScreenBinding

class UserRegistrationScreenActivity : AppCompatActivity() {

    private lateinit var binding: UserRegistrationScreenBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = UserRegistrationScreenBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // ここに登録ボタンの処理などを記述していきます
    }
}
