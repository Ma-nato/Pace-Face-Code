package com.example.paceface

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
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: LoginScreenBinding
    private lateinit var appDatabase: AppDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = LoginScreenBinding.inflate(layoutInflater)
        setContentView(binding.root)

        appDatabase = AppDatabase.getDatabase(this)

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
            binding.errorMessage.text = "ユーザー名とパスワードを入力してください"
            binding.errorMessage.visibility = View.VISIBLE
            return
        }

        lifecycleScope.launch {
            // Find user by username
            val user = appDatabase.userDao().getUserByName(username)

            if (user != null && user.password == password) {
                // Login success
                // Navigate to HomeScreen and clear task
                val intent = Intent(this@LoginActivity, HomeScreenActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            } else {
                // Login failed
                binding.errorMessage.text = "※ユーザー名またはパスワードが違います"
                binding.errorMessage.visibility = View.VISIBLE
            }
        }
    }
}
