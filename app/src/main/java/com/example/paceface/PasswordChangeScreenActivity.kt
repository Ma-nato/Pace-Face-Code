package com.example.paceface

import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.paceface.R

class PasswordChangeScreenActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.password_change_screen) // ← XML のファイル名に変更して

        // --- View 紐付け ---
        val btnBack = findViewById<ImageButton>(R.id.button)

        val etCurrent = findViewById<EditText>(R.id.textView6)
        val etNew = findViewById<EditText>(R.id.textView3)
        val etConfirm = findViewById<EditText>(R.id.textView10)

        val errorCurrent = findViewById<TextView>(R.id.error_current_password)
        val errorNew = findViewById<TextView>(R.id.error_new_password)
        val errorConfirm = findViewById<TextView>(R.id.error_confirm_password)

        val btnChange = findViewById<Button>(R.id.button2)


        // --- 戻るボタン ---
        btnBack.setOnClickListener {
            finish()
        }


        // --- 変更ボタン押したとき ---
        btnChange.setOnClickListener {

            // 一旦全部隠す
            errorCurrent.visibility = View.GONE
            errorNew.visibility = View.GONE
            errorConfirm.visibility = View.GONE

            val currentPw = etCurrent.text.toString()
            val newPw = etNew.text.toString()
            val confirmPw = etConfirm.text.toString()

            var isValid = true

            // ★ 現在のパスワードチェック（ここでは仮に "1234" と一致必須にしてる）
            if (currentPw != "1234") {
                errorCurrent.visibility = View.VISIBLE
                isValid = false
            }

            // ★ 新しいパスワード 8文字以上
            if (newPw.length < 8) {
                errorNew.visibility = View.VISIBLE
                isValid = false
            }

            // ★ 確認用パスワード一致チェック
            if (newPw != confirmPw) {
                errorConfirm.visibility = View.VISIBLE
                isValid = false
            }

            // 全て OK の場合
            if (isValid) {
                Toast.makeText(this, "パスワードを更新しました！", Toast.LENGTH_SHORT).show()
                // 実際のパスワード変更処理を書くならここに追加
            }
        }
    }
}
