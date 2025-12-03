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
            val currentPw = etCurrent.text.toString()
            val newPw = etNew.text.toString()
            val confirmPw = etConfirm.text.toString()

            errorCurrent.visibility = View.GONE
            errorNew.visibility = View.GONE
            errorConfirm.visibility = View.GONE

            val sharedPrefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
            val userId = sharedPrefs.getInt("LOGGED_IN_USER_ID", -1)

            if (userId == -1) {
                Toast.makeText(this, "ユーザー情報が取得できませんでした。", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            lifecycleScope.launch {
                val user = db.userDao().getUserById(userId)

                if (user == null) {
                    runOnUiThread {
                        Toast.makeText(this@PasswordChangeScreenActivity, "ユーザーが見つかりません", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                // --- ハッシュ化してパスワードを比較 ---
                val isCurrentPasswordValid = user.password == User.hashPassword(currentPw)
                val isNewPasswordLongEnough = newPw.length >= 8
                val doNewPasswordsMatch = newPw == confirmPw

                if (isCurrentPasswordValid && isNewPasswordLongEnough && doNewPasswordsMatch) {
                    // --- 検証成功：新しいパスワードをハッシュ化してDBを更新 ---
                    val updatedUser = user.copy(password = User.hashPassword(newPw))
                    db.userDao().update(updatedUser)

                    runOnUiThread {
                        val intent = Intent(this@PasswordChangeScreenActivity, PasswordChangeCompleteScreenActivity::class.java)
                        startActivity(intent)
                        finish()
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
