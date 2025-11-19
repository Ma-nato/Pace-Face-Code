package com.example.paceface

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.example.paceface.databinding.HelpScreenBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: HelpScreenBinding

    override fun onCreate(savedInstanceState: Bundle?) {

        // ★ 最初に SplashScreen をセット
        installSplashScreen()

        super.onCreate(savedInstanceState)

        binding = HelpScreenBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // ボタンの処理などはこのまま
        binding.btnBack.setOnClickListener { finish() }
        binding.homeButton.setOnClickListener { /*TODO*/ }
        binding.passingButton.setOnClickListener { /*TODO*/ }
        binding.historyButton.setOnClickListener { /*TODO*/ }
        binding.emotionButton.setOnClickListener { /*TODO*/ }
        binding.gearButton.setOnClickListener { /*TODO*/ }
    }
}
