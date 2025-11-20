package com.example.paceface

import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class PasswordChangeCompleteScreenActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.password_change_complete_screen)

        val btnOk = findViewById<Button>(R.id.btn_ok)

        // OK ボタン押下時の処理
        btnOk.setOnClickListener {
            // 前の画面へ戻る
            finish()

            // もしホームへ飛ばしたいなら以下
            // val intent = Intent(this, HomeActivity::class.java)
            // startActivity(intent)
        }
    }
}
