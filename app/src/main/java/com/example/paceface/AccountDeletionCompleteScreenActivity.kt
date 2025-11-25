package com.example.paceface

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class AccountDeletionCompleteScreenActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // このActivityが表示するレイアウトファイルを指定します
        setContentView(R.layout.account_deletion_complete_screen)

        // XMLレイアウトから "OK" ボタンを見つけます
        val okButton: Button = findViewById(R.id.button_ok)

        // "OK" ボタンがクリックされたときの動作を定義します
        okButton.setOnClickListener {
            // これまでの画面の履歴をすべて消去し、
            // アプリの最初の選択画面に戻るための準備をします
            val intent = Intent(this, SelectionScreenActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            // 新しい画面を開始します（選択画面に移動）
            startActivity(intent)
        }
    }
}
