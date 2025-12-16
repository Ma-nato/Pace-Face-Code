//AccountDeletionConfirmationScreenActivity.kt
package com.example.paceface

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class AccountDeletionConfirmationScreenActivity : AppCompatActivity() {

    private lateinit var db: AppDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.account_deletion_confirmation_screen)

        db = AppDatabase.getDatabase(this)

        // XMLから「戻る」ボタンと「削除」ボタンを取得
        val backButton: Button = findViewById(R.id.button_back)
        val deleteButton: Button = findViewById(R.id.button_delete)

        // 「戻る」ボタンがクリックされた時の動作
        backButton.setOnClickListener {
            finish()
        }

        // 「削除」ボタンがクリックされた時の動作
        deleteButton.setOnClickListener {
            val sharedPrefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
            val userId = sharedPrefs.getInt("LOGGED_IN_USER_ID", -1)

            if (userId == -1) {
                Toast.makeText(this, "ユーザー情報が取得できませんでした。", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            lifecycleScope.launch {
                val user = db.userDao().getUserById(userId)

                if (user != null) {
                    // --- DBからユーザーを削除 ---
                    db.userDao().delete(user)

                    // --- SharedPreferencesからIDを削除 ---
                    with(sharedPrefs.edit()) {
                        remove("LOGGED_IN_USER_ID")
                        apply()
                    }

                    // --- UIスレッドで完了画面へ遷移 ---
                    runOnUiThread {
                        // アカウント削除完了画面へ遷移
                        val intent = Intent(this@AccountDeletionConfirmationScreenActivity, AccountDeletionCompleteScreenActivity::class.java)
                        // 削除完了画面から戻れないようにスタックをクリア
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                        finish()
                    }
                } else {
                    runOnUiThread {
                        Toast.makeText(this@AccountDeletionConfirmationScreenActivity, "ユーザーが見つかりませんでした。", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
}
