//PasswordChangeScreenActivity.kt
package com.example.paceface

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.paceface.R
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class PasswordChangeScreenActivity : AppCompatActivity() {

    private lateinit var db: AppDatabase
    private val auth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.password_change_screen)

        db = AppDatabase.getDatabase(this)

        // --- View 紐付け ---
        val btnBack = findViewById<ImageButton>(R.id.backButton)

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
                val user = withContext(Dispatchers.IO) { db.userDao().getUserById(userId) }

                if (user == null) {
                    Toast.makeText(this@PasswordChangeScreenActivity, "ユーザーが見つかりません", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                // --- バリデーション ---
                val isCurrentPasswordValid = user.password == User.hashPassword(currentPw)
                val isNewPasswordLongEnough = newPw.length >= 8
                val doNewPasswordsMatch = newPw == confirmPw

                if (isCurrentPasswordValid && isNewPasswordLongEnough && doNewPasswordsMatch) {
                    try {
                        // 1. Firebase Auth のパスワード更新
                        val firebaseUser = auth.currentUser
                        if (firebaseUser != null) {
                            firebaseUser.updatePassword(newPw).await()
                        } else {
                            throw Exception("Firebaseユーザーが認証されていません。再ログインしてください。")
                        }

                        // 2. ローカルDBの更新
                        val updatedUser = user.copy(password = User.hashPassword(newPw))
                        withContext(Dispatchers.IO) { db.userDao().update(updatedUser) }

                        Toast.makeText(this@PasswordChangeScreenActivity, "パスワードを変更しました", Toast.LENGTH_SHORT).show()
                        val intent = Intent(this@PasswordChangeScreenActivity, PasswordChangeCompleteScreenActivity::class.java)
                        startActivity(intent)
                        finish()

                    } catch (e: Exception) {
                        Toast.makeText(this@PasswordChangeScreenActivity, "エラー: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                    }
                } else {
                    // --- 検証失敗：エラー表示 ---
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