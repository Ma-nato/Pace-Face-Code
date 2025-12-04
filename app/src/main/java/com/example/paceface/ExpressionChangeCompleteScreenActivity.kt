package com.example.paceface

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class ExpressionChangeCompleteScreenActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // このActivityが表示するレイアウトファイルを指定します
        setContentView(R.layout.expression_change_complete_screen)

        // XMLからOKボタンを見つけます
        val okButton: Button = findViewById(R.id.btn_ok)

        // 「OK」ボタンがクリックされたときの動作を定義します
        okButton.setOnClickListener {
            // ホーム画面へ遷移
            val intent = Intent(this, HomeScreenActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            startActivity(intent)
        }
    }
}