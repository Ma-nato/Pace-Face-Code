package com.example.paceface

import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class ExpressionChangeCompleteScreenActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.expression_change_complete_screen)

        val okButton: Button = findViewById(R.id.btn_ok)

        // 「OK」ボタンがクリックされたら、この画面を閉じて前の画面に戻るのが正しい動作です
        okButton.setOnClickListener {
            finish()
        }
    }
}
