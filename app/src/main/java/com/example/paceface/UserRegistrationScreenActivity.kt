package com.example.paceface

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.method.PasswordTransformationMethod
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.paceface.databinding.UserRegistrationScreenBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class UserRegistrationScreenActivity : AppCompatActivity() {

    private lateinit var binding: UserRegistrationScreenBinding
    private lateinit var appDatabase: AppDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = UserRegistrationScreenBinding.inflate(layoutInflater)
        setContentView(binding.root)

        appDatabase = AppDatabase.getDatabase(this)

        setupPasswordVisibilityToggles()

        binding.btnRegister.setOnClickListener {
            if (validateInputs()) {
                checkDuplicatesAndProceed()
            }
        }
    }

    private fun setupPasswordVisibilityToggles() {
        setupPasswordToggle(binding.etPassword, binding.btnPasswordEye)
        setupPasswordToggle(binding.etPassword2, binding.btnPassword2Eye)
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

    private fun validateInputs(): Boolean {
        binding.tvUserNameError.visibility = View.GONE
        binding.tvEmailError.visibility = View.GONE
        binding.tvPasswordError.visibility = View.GONE
        binding.tvPassword2Error.visibility = View.GONE

        var isValid = true

        if (binding.etUsername.text.toString().trim().isEmpty()) {
            binding.tvUserNameError.text = "※ユーザー名が入力されていません"
            binding.tvUserNameError.visibility = View.VISIBLE
            isValid = false
        }

        val email = binding.etEmail.text.toString().trim()
        if (email.isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.tvEmailError.text = "※正しいメールアドレスを入力してください"
            binding.tvEmailError.visibility = View.VISIBLE
            isValid = false
        }

        val password = binding.etPassword.text.toString()
        if (password.length < 8) {
            binding.tvPasswordError.visibility = View.VISIBLE
            isValid = false
        }

        if (password != binding.etPassword2.text.toString()) {
            binding.tvPassword2Error.visibility = View.VISIBLE
            isValid = false
        }

        return isValid
    }

    private fun checkDuplicatesAndProceed() {
        val name = binding.etUsername.text.toString().trim()
        val email = binding.etEmail.text.toString().trim()

        lifecycleScope.launch(Dispatchers.IO) { // Use IO dispatcher for DB operations
            try {
                val userByName = appDatabase.userDao().getUserByName(name)
                if (userByName != null) {
                    withContext(Dispatchers.Main) { // Switch to Main for UI update
                        binding.tvUserNameError.text = "※このユーザー名は既に使用されています"
                        binding.tvUserNameError.visibility = View.VISIBLE
                    }
                    return@launch
                }

                val userByEmail = appDatabase.userDao().getUserByEmail(email)
                if (userByEmail != null) {
                    withContext(Dispatchers.Main) { // Switch to Main for UI update
                        binding.tvEmailError.text = "※このメールアドレスは既に使用されています"
                        binding.tvEmailError.visibility = View.VISIBLE
                    }
                    return@launch
                }

                // No duplicates, proceed to confirmation screen on Main thread
                withContext(Dispatchers.Main) {
                    val password = binding.etPassword.text.toString()
                    val intent = Intent(this@UserRegistrationScreenActivity, UserRegistrationConfirmationScreenActivity::class.java).apply {
                        putExtra("USER_NAME", name)
                        putExtra("USER_EMAIL", email)
                        putExtra("USER_PASSWORD", password)
                    }
                    startActivity(intent)
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) { // Switch to Main for Toast
                    Toast.makeText(this@UserRegistrationScreenActivity, "データベース確認中にエラーが発生しました。", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
