package com.example.paceface

import android.content.Context
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
import com.google.firebase.auth.ActionCodeSettings
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class UserRegistrationScreenActivity : AppCompatActivity() {

    private lateinit var binding: UserRegistrationScreenBinding
    private lateinit var appDatabase: AppDatabase
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = UserRegistrationScreenBinding.inflate(layoutInflater)
        setContentView(binding.root)

        appDatabase = AppDatabase.getDatabase(this)
        auth = Firebase.auth

        setupPasswordVisibilityToggles()

        binding.btnRegister.setOnClickListener {
            if (validateInputs()) {
                checkDuplicatesAndRegister()
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
            binding.tvPasswordError.visibility = View.VISIBLE
            isValid = false
        }

        if (password != binding.etPassword2.text.toString()) {
            binding.tvPassword2Error.visibility = View.VISIBLE
            isValid = false
        }

        return isValid
    }

    private fun checkDuplicatesAndRegister() {
        val name = binding.etUsername.text.toString().trim()
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString()

        lifecycleScope.launch(Dispatchers.IO) {
            val userByName = appDatabase.userDao().getUserByName(name)
            if (userByName != null) {
                withContext(Dispatchers.Main) {
                    binding.tvUserNameError.text = "※このユーザー名は既に使用されています"
                    binding.tvUserNameError.visibility = View.VISIBLE
                }
                return@launch
            }

            val userByEmail = appDatabase.userDao().getUserByEmail(email)
            if (userByEmail != null) {
                withContext(Dispatchers.Main) {
                    binding.tvEmailError.text = "※このメールアドレスは既に使用されています"
                    binding.tvEmailError.visibility = View.VISIBLE
                }
                return@launch
            }

            withContext(Dispatchers.Main) {
                try {
                    val result = auth.createUserWithEmailAndPassword(email, password).await()
                    val user = result.user

                    // ActionCodeSettings for email verification link
                    val actionCodeSettings = ActionCodeSettings.newBuilder()
                        .setUrl("https://pace-face-18862.firebaseapp.com") // あなたのプロジェクトのドメインに修正
                        // Replace with your domain
                        .setHandleCodeInApp(true)
                        .setAndroidPackageName(
                            "com.example.paceface",
                            true, /* installIfNotAvailable */
                            null /* minimumVersion */)
                        .build()

                    user?.sendEmailVerification(actionCodeSettings)?.await()

                    // Save email for verification link
                    val sharedPrefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
                    with(sharedPrefs.edit()) {
                        putString("EMAIL_FOR_VERIFICATION", email)
                        apply()
                    }

                    // Insert user in background
                    lifecycleScope.launch(Dispatchers.IO) {
                        val newUser = User(email = email, name = name, password = User.hashPassword(password))
                        appDatabase.userDao().insert(newUser)
                    }

                    val intent = Intent(this@UserRegistrationScreenActivity, EmailVerificationScreenActivity::class.java).apply {
                        putExtra("USER_EMAIL", email)
                    }
                    startActivity(intent)
                    finish()

                } catch (e: Exception) {
                    Toast.makeText(this@UserRegistrationScreenActivity, "登録に失敗しました: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
