package com.example.paceface

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.paceface.databinding.HomeScreenBinding

class HomeScreenActivity : AppCompatActivity() {

    private lateinit var binding: HomeScreenBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = HomeScreenBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 現在の画面に対応するボタンの背景色を変更
        binding.homeButton.setBackgroundColor(ContextCompat.getColor(this, R.color.selected_nav_item_bg))

        // すれちがいボタンがクリックされた時の処理
        binding.passingButton.setOnClickListener {
            val intent = Intent(this, ProximityHistoryScreenActivity::class.java)
            startActivity(intent)
            overridePendingTransition(0, 0) // 遷移時のアニメーションを無効化
        }

        // ここに速度表示の更新などのロジックを記述していきます
    }
}
