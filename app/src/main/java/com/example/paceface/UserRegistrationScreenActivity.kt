//UserRegistrationScreenActivity.kt
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
import com.example.paceface.databinding.UserRegistrationScreenBinding
import com.google.firebase.auth.ActionCodeSettings
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import android.util.Log
import com.google.android.material.snackbar.Snackbar

class UserRegistrationScreenActivity : AppCompatActivity() {

    private lateinit var binding: UserRegistrationScreenBinding
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = UserRegistrationScreenBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = Firebase.auth

        setupPasswordVisibilityToggles()

        binding.btnBack.setOnClickListener {
            val intent = Intent(this, SelectionScreenActivity::class.java)
            startActivity(intent)
            finish()
        }

        binding.btnRegister.setOnClickListener {
            if (validateInputs()) {
                createAccountAndSendVerificationEmail()
            }
        }
    }

    private fun setupPasswordVisibilityToggles() {
        setupPasswordToggle(binding.etPassword, binding.btnPasswordEye)
        setupPasswordToggle(binding.etPassword2, binding.btnPassword2Eye)
    }

    private fun setupPasswordToggle(editText: EditText, eyeButton: ImageButton) {
        editText.transformationMethod = PasswordTransformationMethod.getInstance()
        eyeButton.setColorFilter(Color.GRAY)

        eyeButton.setOnClickListener {
            if (editText.transformationMethod == null) {
                editText.transformationMethod = PasswordTransformationMethod.getInstance()
                eyeButton.setColorFilter(Color.GRAY)
            } else {
                editText.transformationMethod = null
                eyeButton.clearColorFilter()
            }
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
            binding.tvPasswordError.text = "※パスワードは8文字以上で入力してください"
            binding.tvPasswordError.visibility = View.VISIBLE
            isValid = false
        }

        if (password != binding.etPassword2.text.toString()) {
            binding.tvPassword2Error.text = "※パスワードが一致しません"
            binding.tvPassword2Error.visibility = View.VISIBLE
            isValid = false
        }
        return isValid
    }

    private fun createAccountAndSendVerificationEmail() {
        val name = binding.etUsername.text.toString().trim()
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString()

        binding.btnRegister.isEnabled = false

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val authResult = auth.createUserWithEmailAndPassword(email, password).await()
                val firebaseUser = authResult.user ?: throw Exception("Firebaseユーザーの作成に失敗しました。")

                // ★ 修正: パスワードの平文保存を削除。名前とメールのみを一時保存。
                val tempPrefs = getSharedPreferences("PendingRegistrations", Context.MODE_PRIVATE)
                with(tempPrefs.edit()) {
                    putString("${email}_name", name)
                    // remove("${email}_password") // 以前のコードから削除
                    apply()
                }
                Log.d("UserRegistration", "一時的にユーザー名とメールをSharedPreferencesに保存しました。")

                val actionCodeSettings = ActionCodeSettings.newBuilder()
                    .setUrl("https://pace-face-18862.firebaseapp.com")
                    .setHandleCodeInApp(true)
                    .setAndroidPackageName("com.example.paceface", true, null)
                    .build()
                firebaseUser.sendEmailVerification(actionCodeSettings).await()
                Log.d("UserRegistration", "メール認証が送信されました。")

                withContext(Dispatchers.Main) {
                    val intent = Intent(this@UserRegistrationScreenActivity, EmailVerificationScreenActivity::class.java).apply {
                        putExtra("USER_EMAIL", email)
                    }
                    startActivity(intent)
                    finish()
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    if (e is FirebaseAuthUserCollisionException) {
                        Snackbar.make(binding.root, "このメールアドレスは既に使用されています。", Snackbar.LENGTH_LONG).show()
                    } else {
                        Snackbar.make(binding.root, "登録に失敗しました: ${e.message}", Snackbar.LENGTH_LONG).show()
                    }
                }
            } finally {
                withContext(Dispatchers.Main) {
                    binding.btnRegister.isEnabled = true
                }
            }
        }
    }
}