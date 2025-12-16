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
            // 遷移先を SelectionScreenActivity に変更
            val intent = Intent(this, SelectionScreenActivity::class.java)
            // 戻る操作なので、現在のActivityを終了する
            startActivity(intent)
            finish()
        }

        binding.btnRegister.setOnClickListener { // btnRegister は登録ボタンのIDと仮定
            if (validateInputs()) {
                // 入力値の検証に成功した場合のみ、アカウント作成とメール送信の処理を実行
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
        Log.d("UserRegistration", "validateInputs() が呼び出されました。")
        binding.tvUserNameError.visibility = View.GONE
        binding.tvEmailError.visibility = View.GONE
        binding.tvPasswordError.visibility = View.GONE
        binding.tvPassword2Error.visibility = View.GONE

        var isValid = true

        if (binding.etUsername.text.toString().trim().isEmpty()) {
            binding.tvUserNameError.text = "※ユーザー名が入力されていません"
            binding.tvUserNameError.visibility = View.VISIBLE
            isValid = false
            Log.d("UserRegistration", "validateInputs: ユーザー名エラー")
        }

        val email = binding.etEmail.text.toString().trim()
        if (email.isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.tvEmailError.text = "※正しいメールアドレスを入力してください"
            binding.tvEmailError.visibility = View.VISIBLE
            isValid = false
            Log.d("UserRegistration", "validateInputs: メールアドレスエラー")
        }

        val password = binding.etPassword.text.toString()
        if (password.length < 8) {
            binding.tvPasswordError.visibility = View.VISIBLE
            isValid = false
            Log.d("UserRegistration", "validateInputs: パスワード長エラー")
        }

        if (password != binding.etPassword2.text.toString()) {
            binding.tvPassword2Error.visibility = View.VISIBLE
            isValid = false
            Log.d("UserRegistration", "validateInputs: パスワード不一致エラー")
        }
        Log.d("UserRegistration", "validateInputs() が終了しました。結果: $isValid")
        return isValid
    }

    private fun createAccountAndSendVerificationEmail() {
        Log.d("UserRegistration", "createAccountAndSendVerificationEmail() が呼び出されました。")
        val name = binding.etUsername.text.toString().trim()
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString()

        binding.btnRegister.isEnabled = false
        Log.d("UserRegistration", "登録ボタンを無効化しました。")

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                Log.d("UserRegistration", "コルーチン内部: Firebase Authでのユーザー作成を試行します。")
                val authResult = auth.createUserWithEmailAndPassword(email, password).await()
                val firebaseUser = authResult.user ?: throw Exception("Firebaseユーザーの作成に失敗しました。")
                Log.d("UserRegistration", "コルーチン内部: Firebaseユーザーが作成されました: ${firebaseUser.uid}")

                // Store registration info temporarily for later use after email verification
                Log.d("UserRegistration", "一時的にユーザー情報をSharedPreferencesに保存します。")
                val tempPrefs = getSharedPreferences("PendingRegistrations", Context.MODE_PRIVATE)
                with(tempPrefs.edit()) {
                    putString("${email}_name", name)
                    putString("${email}_password", password) // Needed for local DB hash
                    apply()
                }
                Log.d("UserRegistration", "一時情報の保存が完了しました。")


                Log.d("UserRegistration", "コルーチン内部: メール認証の送信を試行します。")
                val actionCodeSettings = ActionCodeSettings.newBuilder()
                    .setUrl("https://pace-face-18862.firebaseapp.com") // This URL will be handled by DeepLinkActivity
                    .setHandleCodeInApp(true)
                    .setAndroidPackageName("com.example.paceface", true, null)
                    .build()
                firebaseUser.sendEmailVerification(actionCodeSettings).await()
                Log.d("UserRegistration", "コルーチン内部: メール認証が送信されました。")

                withContext(Dispatchers.Main) {
                    Log.i("UserRegistration", "コルーチン内部: 登録成功。EmailVerificationScreenActivityへ遷移します。")
                    val intent = Intent(this@UserRegistrationScreenActivity, EmailVerificationScreenActivity::class.java).apply {
                        putExtra("USER_EMAIL", email)
                    }
                    startActivity(intent)
                    finish()
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    if (e is FirebaseAuthUserCollisionException) {
                        Log.w("UserRegistration", "このメールアドレスは既に使用されています。", e)
                        Snackbar.make(binding.root, "このメールアドレスは既に使用されています。", Snackbar.LENGTH_LONG).show()
                    } else {
                        Log.e("UserRegistration", "コルーチン内部: 登録処理中にエラーが発生しました: ${e.message}", e)
                        Snackbar.make(binding.root, "登録に失敗しました: ${e.message}", Snackbar.LENGTH_LONG).show()
                    }
                }
            } finally {
                withContext(Dispatchers.Main) {
                    binding.btnRegister.isEnabled = true
                    Log.d("UserRegistration", "コルーチン内部: 登録処理が終了しました。ボタンを再度有効にしました。")
                }
            }
        }
    }
}