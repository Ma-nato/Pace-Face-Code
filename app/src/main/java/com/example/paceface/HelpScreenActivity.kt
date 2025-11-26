package com.example.paceface

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.paceface.databinding.HelpScreenBinding

class HelpScreenActivity : AppCompatActivity() {

    private lateinit var binding: HelpScreenBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = HelpScreenBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener {
            finish() // この画面を閉じて前の画面に戻る
        }
        binding.homeButton.setOnClickListener { /*TODO*/ }
        binding.passingButton.setOnClickListener { /*TODO*/ }
        binding.historyButton.setOnClickListener { /*TODO*/ }
        binding.emotionButton.setOnClickListener { /*TODO*/ }
        binding.gearButton.setOnClickListener { /*TODO*/ }
    }
}
