package com.example.paceface

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.paceface.databinding.HomeScreenBinding

class HomeScreenActivity : AppCompatActivity() {

    private lateinit var binding: HomeScreenBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = HomeScreenBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // ここに速度表示の更新などのロジックを記述していきます
    }
}
