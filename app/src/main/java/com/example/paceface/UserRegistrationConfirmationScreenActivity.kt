package com.example.paceface

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.paceface.databinding.UserRegistrationConfirmationScreenBinding
import kotlinx.coroutines.launch

class UserRegistrationConfirmationScreenActivity : AppCompatActivity() {

    private lateinit var binding: UserRegistrationConfirmationScreenBinding
    private lateinit var appDatabase: AppDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = UserRegistrationConfirmationScreenBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // データベースインスタンスを取得
        appDatabase = AppDatabase.getDatabase(this)

        // Intentからデータを取得
        val userName = intent.getStringExtra("USER_NAME")
        val email = intent.getStringExtra("USER_EMAIL")
        val password = intent.getStringExtra("USER_PASSWORD")

        // 取得したデータをTextViewに設定
        binding.tvUserNameValue.text = userName
        binding.tvEmailValue.text = email
        // パスワードは伏字で表示
        binding.tvPasswordValue.text = "●".repeat(password?.length ?: 0)

        // 「戻る」ボタンがクリックされた時の処理
        binding.btnBack.setOnClickListener {
            finish()
        }

        // 「登録」ボタンがクリックされた時の処理
        binding.btnRegister.setOnClickListener {
            registerUserAndNavigate(userName, email, password)
        }
    }

    private fun registerUserAndNavigate(userName: String?, email: String?, password: String?) {
        if (userName == null || email == null || password == null) {
            Toast.makeText(this, "登録情報が不足しています。前の画面に戻ってやり直してください。", Toast.LENGTH_LONG).show()
            return
        }

        // パスワードを共通の関数でハッシュ化して保存
        val hashedPassword = User.hashPassword(password)

        val user = User(
            name = userName,
            email = email,
            password = hashedPassword // ハッシュ化されたパスワードを保存
        )

        lifecycleScope.launch {
            try {
                appDatabase.userDao().insert(user)
                Toast.makeText(this@UserRegistrationConfirmationScreenActivity, "登録が完了しました", Toast.LENGTH_SHORT).show()

                // 登録完了画面へ遷移し、途中の画面は消去する
                val intent = Intent(this@UserRegistrationConfirmationScreenActivity, UserRegistrationCompleteScreenActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()

            } catch (e: Exception) {
                // Handle potential conflicts (e.g., email or name already exists)
                if (e is android.database.sqlite.SQLiteConstraintException) {
                    Toast.makeText(this@UserRegistrationConfirmationScreenActivity, "ユーザー名またはメールアドレスが既に使用されています。", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this@UserRegistrationConfirmationScreenActivity, "登録に失敗しました: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
