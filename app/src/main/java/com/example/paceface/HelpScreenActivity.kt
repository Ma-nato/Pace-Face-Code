package com.example.paceface

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
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
        setupNavigation() // ここでsetupNavigation()を呼び出す
    }

    private fun setupNavigation() {

        binding.homeButton.setOnClickListener {
            val intent = Intent(this, HomeScreenActivity::class.java)
            startActivity(intent)
            overridePendingTransition(0, 0)
        }

        binding.passingButton.setOnClickListener {
            val intent = Intent(this, ProximityHistoryScreenActivity::class.java)
            startActivity(intent)
            overridePendingTransition(0, 0)
        }

        binding.historyButton.setOnClickListener {
            val intent = Intent(this, HistoryScreenActivity::class.java)
            startActivity(intent)
            overridePendingTransition(0, 0)
        }

        binding.emotionButton.setOnClickListener {
            // TODO: EmotionScreenActivity.kt が存在すれば、ここに遷移ロジックを実装する
            // 例：val intent = Intent(this, EmotionScreenActivity::class.java)
            // 例：startActivity(intent)
            // 例：overridePendingTransition(0, 0)
        }

        binding.gearButton.setOnClickListener {
            val intent = Intent(this, UserSettingsScreenActivity::class.java)
            startActivity(intent)
            overridePendingTransition(0, 0)
        }
    }
}
