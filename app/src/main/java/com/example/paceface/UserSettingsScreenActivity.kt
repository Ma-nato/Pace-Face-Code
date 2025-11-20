package com.example.paceface

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.paceface.databinding.UserSettingsScreenBinding

class UserSettingsScreenActivity : AppCompatActivity() {

    private lateinit var binding: UserSettingsScreenBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = UserSettingsScreenBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 現在の画面に対応するボタンの背景色を変更
        binding.gearButton.setBackgroundColor(ContextCompat.getColor(this, R.color.selected_nav_item_bg))

        binding.homeButton.setOnClickListener {
            val intent = Intent(this, HomeScreenActivity::class.java)
            startActivity(intent)
            overridePendingTransition(0, 0) // 遷移時のアニメーションを無効化
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

        binding.gearButton.setOnClickListener {
            // 現在の画面なので何もしない
        }
    }
}
