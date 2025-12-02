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
        // NavigationUtils を使用して共通ナビゲーションをセットアップ
        // このActivityはナビゲーションバーの主要な画面ではないため、どれもハイライトされない
        NavigationUtils.setupCommonNavigation(
            this,
            HelpScreenActivity::class.java,
            binding.homeButton,
            binding.passingButton,
            binding.historyButton,
            binding.emotionButton,
            binding.gearButton
        )
    }
}
