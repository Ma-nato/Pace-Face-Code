package com.example.paceface

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.method.PasswordTransformationMethod
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.paceface.databinding.LoginScreenBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: LoginScreenBinding
    private lateinit var appDatabase: AppDatabase
    private lateinit var tokenManager: TokenManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = LoginScreenBinding.inflate(layoutInflater)
        setContentView(binding.root)

        appDatabase = AppDatabase.getDatabase(this)
        tokenManager = TokenManager(this)

        // パスワード表示/非表示のトグルを設定
        setupPasswordToggle(binding.inputPassword, binding.btnEye)

        binding.btnLogin.setOnClickListener {
            login()
        }
    }

    private fun setupPasswordToggle(editText: EditText, eyeButton: ImageButton) {
        // 初期状態：パスワードは非表示、アイコンはグレー
        editText.transformationMethod = PasswordTransformationMethod.getInstance()
        eyeButton.setColorFilter(Color.GRAY)

        eyeButton.setOnClickListener {
            if (editText.transformationMethod == null) {
                // 現在パスワードが表示されている場合 -> 非表示に
                editText.transformationMethod = PasswordTransformationMethod.getInstance()
                eyeButton.setColorFilter(Color.GRAY)
            } else {
                // 現在パスワードが非表示の場合 -> 表示に
                editText.transformationMethod = null
                eyeButton.clearColorFilter()
            }
            // カーソルを末尾に移動
            editText.setSelection(editText.text.length)
        }
    }

    private fun login() {
        val username = binding.inputUsername.text.toString().trim()
        val password = binding.inputPassword.text.toString()

        // Hide previous error
        binding.errorMessage.visibility = View.INVISIBLE

        if (username.isEmpty() || password.isEmpty()) {
            binding.errorMessage.text = getString(R.string.error_empty_username_password)
            binding.errorMessage.visibility = View.VISIBLE
            return
        }

        lifecycleScope.launch {
            val user = withContext(Dispatchers.IO) {
                appDatabase.userDao().getUserByName(username)
            }

            // 入力されたパスワードをハッシュ化して比較
            val hashedPassword = User.hashPassword(password)
            if (user != null && user.password == hashedPassword) {
                // Login success

                // --- Generate and save dummy tokens ---
                val accessToken = UUID.randomUUID().toString()
                val refreshToken = UUID.randomUUID().toString()
                tokenManager.saveTokens(AuthToken(accessToken, refreshToken))

                // Navigate to HomeScreen and clear task
                val intent = Intent(this@LoginActivity, HomeScreenActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            } else {
                // Login failed
                binding.errorMessage.text = getString(R.string.error_invalid_username_password)
                binding.errorMessage.visibility = View.VISIBLE
            }
        }
    }
}
