package com.example.paceface

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.paceface.R
import kotlinx.coroutines.launch

class PasswordChangeScreenActivity : AppCompatActivity() {

    private lateinit var db: AppDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.password_change_screen)

        db = AppDatabase.getDatabase(this)

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
            // --- 入力値を取得 ---
            val currentPw = etCurrent.text.toString()
            val newPw = etNew.text.toString()
            val confirmPw = etConfirm.text.toString()

            // --- エラーメッセージを一旦非表示 ---
            errorCurrent.visibility = View.GONE
            errorNew.visibility = View.GONE
            errorConfirm.visibility = View.GONE

            // --- SharedPreferencesからログイン中のユーザーIDを取得 ---
            val sharedPrefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
            val userId = sharedPrefs.getInt("LOGGED_IN_USER_ID", -1)

            if (userId == -1) {
                Toast.makeText(this, "ユーザー情報が取得できませんでした。", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            lifecycleScope.launch {
                // --- データベースからユーザー情報を非同期で取得 ---
                val user = db.userDao().getUserById(userId)

                // --- パスワード検証ロジック ---
                val isCurrentPasswordValid = user != null && user.password == currentPw
                val isNewPasswordLongEnough = newPw.length >= 8
                val doNewPasswordsMatch = newPw == confirmPw

                if (isCurrentPasswordValid && isNewPasswordLongEnough && doNewPasswordsMatch) {
                    // --- 検証成功：DBを更新 ---
                    val updatedUser = user!!.copy(password = newPw)
                    db.userDao().update(updatedUser)

                    // --- UIスレッドで成功画面へ遷移 & 画面を閉じる ---
                    runOnUiThread {
                        val intent = Intent(this@PasswordChangeScreenActivity, PasswordChangeCompleteScreenActivity::class.java)
                        startActivity(intent)
                        finish() // パスワード変更画面をスタックから削除
                    }
                } else {
                    // --- 検証失敗：UIスレッドでエラー表示 ---
                    runOnUiThread {
                        if (!isCurrentPasswordValid) {
                            errorCurrent.visibility = View.VISIBLE
                        }
                        if (!isNewPasswordLongEnough) {
                            errorNew.visibility = View.VISIBLE
                        }
                        if (!doNewPasswordsMatch) {
                            errorConfirm.visibility = View.VISIBLE
                        }
                    }
                }
            }
        }
    }
}
