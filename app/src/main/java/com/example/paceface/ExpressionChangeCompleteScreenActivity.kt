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
            // この完了画面と、下にある編集画面の両方を閉じて、
            // 設定画面に戻るためのIntentを作成します。
            val intent = Intent(this, UserSettingsScreenActivity::class.java).apply {
                // FLAG_ACTIVITY_CLEAR_TOP: 呼び出すActivityより上にある画面をすべて消去
                // FLAG_ACTIVITY_SINGLE_TOP: すでに存在する場合は新しいインスタンスを作らない
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            setContentView(R.layout.expression_change_complete_screen)
              //OKボタン
            val btnOk = findViewById<Button>(R.id.btn_ok)

            // OK ボタン押下時の処理
            btnOk.setOnClickListener {
                // ホーム画面へ遷移
                val intent = Intent(this, HomeScreenActivity::class.java)
                startActivity(intent)
            }
        }
    }
}