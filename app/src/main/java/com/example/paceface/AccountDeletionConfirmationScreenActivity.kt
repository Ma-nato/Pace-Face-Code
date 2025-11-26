package com.example.paceface

import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class AccountDeletionConfirmationScreenActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // このActivityが使用するレイアウトファイルを指定
        setContentView(R.layout.account_deletion_confirmation_screen)

        // XMLから「戻る」ボタンと「削除」ボタンを取得
        val backButton: Button = findViewById(R.id.button_back)
        val deleteButton: Button = findViewById(R.id.button_delete)

        // 「戻る」ボタンがクリックされた時の動作
        backButton.setOnClickListener {
            // この画面を閉じる
            finish()
        }

        // 「削除」ボタンがクリックされた時の動作
        deleteButton.setOnClickListener {
            // "アカウントを削除しました" というメッセージを表示
            Toast.makeText(this, "アカウントを削除しました", Toast.LENGTH_SHORT).show()
            // TODO: ここに実際の削除処理を後で追加します
        }
    }
}
