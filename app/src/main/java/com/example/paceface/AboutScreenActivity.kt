package com.example.paceface

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.paceface.databinding.AboutScreenBinding

class AboutScreenActivity : AppCompatActivity() {

    private lateinit var binding: AboutScreenBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = AboutScreenBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 戻るボタンの処理
        binding.backButton.setOnClickListener {
            finish()
        }

        // お問い合わせリンクの処理
        binding.contactLink.setOnClickListener {
            sendContactEmail()
        }
        
        // 下部ナビゲーションのセットアップ
        setupNavigation()
    }

    private fun sendContactEmail() {
        val email = "20233130@kcska.onmicrosoft.com"
        val subject = "Pace Faceに関するお問い合わせ"

        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:") // メーラーアプリのみを対象とする
            putExtra(Intent.EXTRA_EMAIL, arrayOf(email))
            putExtra(Intent.EXTRA_SUBJECT, subject)
        }

        // メールアプリが端末に存在するか確認
        if (intent.resolveActivity(packageManager) != null) {
            startActivity(intent)
        } else {
            Toast.makeText(this, "メールアプリが見つかりません。", Toast.LENGTH_SHORT).show()
        }
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
            // TODO: EmotionScreenActivity.kt を作成し、遷移を実装する
        }

        binding.gearButton.setOnClickListener {
            val intent = Intent(this, UserSettingsScreenActivity::class.java)
            startActivity(intent)
            overridePendingTransition(0, 0)
        }
    }
}
