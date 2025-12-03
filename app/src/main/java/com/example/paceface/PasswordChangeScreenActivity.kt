package com.example.paceface

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.paceface.databinding.PasswordChangeScreenBinding // Bindingクラスをインポート
import kotlinx.coroutines.launch

class PasswordChangeScreenActivity : AppCompatActivity() {

    private lateinit var binding: PasswordChangeScreenBinding // Bindingクラスを使用
    private lateinit var db: AppDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = PasswordChangeScreenBinding.inflate(layoutInflater) // Bindingクラスを初期化
        setContentView(binding.root)

        db = AppDatabase.getDatabase(this)

        // --- View 紐付けはbindingを使用 ---

        // --- 戻るボタン ---
        binding.button.setOnClickListener { // bindingを使用
            finish()
        }

        // --- 変更ボタン押したとき ---
        binding.button2.setOnClickListener { // bindingを使用
            // --- 入力値を取得 ---
            val currentPw = binding.textView6.text.toString() // bindingを使用
            val newPw = binding.textView3.text.toString() // bindingを使用
            val confirmPw = binding.textView10.text.toString() // bindingを使用

            // --- エラーメッセージを一旦非表示 ---
            binding.errorCurrentPassword.visibility = View.GONE // bindingを使用
            binding.errorNewPassword.visibility = View.GONE // bindingを使用
            binding.errorConfirmPassword.visibility = View.GONE // bindingを使用

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
                            binding.errorCurrentPassword.visibility = View.VISIBLE // bindingを使用
                        }
                        if (!isNewPasswordLongEnough) {
                            binding.errorNewPassword.visibility = View.VISIBLE // bindingを使用
                        }
                        if (!doNewPasswordsMatch) {
                            binding.errorConfirmPassword.visibility = View.VISIBLE // bindingを使用
                        }
                    }
                }
            }
        }

        // NavigationUtils を使用して共通ナビゲーションをセットアップ
        // このActivityはナビゲーションバーの主要な画面ではないため、どれもハイライトされない
        NavigationUtils.setupCommonNavigation(
            this,
            PasswordChangeScreenActivity::class.java,
            binding.homeButton,
            binding.passingButton,
            binding.historyButton,
            binding.emotionButton,
            binding.gearButton
        )
    }
}