//LoginActivity.kt
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
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.google.firebase.firestore.ktx.firestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import android.util.Log
import android.database.sqlite.SQLiteConstraintException // 追加

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: LoginScreenBinding
    private lateinit var tokenManager: TokenManager
    private lateinit var auth: FirebaseAuth
    private val db = Firebase.firestore
    private lateinit var appDatabase: AppDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = LoginScreenBinding.inflate(layoutInflater)
        setContentView(binding.root)

        tokenManager = TokenManager(this)
        auth = Firebase.auth
        appDatabase = AppDatabase.getDatabase(this)

        // 以前のログイン情報をクリア（DBは消さない）
        tokenManager.clearTokens()
        val sharedPrefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        with(sharedPrefs.edit()) {
            remove("LOGGED_IN_USER_ID")
            remove("LOGGED_IN_FIREBASE_UID")
            apply()
        }
        Log.d("LoginActivity", "Debug: Old auth info cleared.")

        setupPasswordToggle(binding.inputPassword, binding.btnEye)

        binding.btnLogin.setOnClickListener {
            Log.d("LoginActivity", "Login button clicked.")
            login()
        }
    }

    private fun setupPasswordToggle(editText: EditText, eyeButton: ImageButton) {
        // (変更なしのため省略)
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

    private fun login() {
        val username = binding.inputUsername.text.toString().trim()
        val password = binding.inputPassword.text.toString()

        binding.errorMessage.visibility = View.INVISIBLE

        if (username.isEmpty()) {
            binding.errorMessage.text = "※ユーザー名を入力してください"
            binding.errorMessage.visibility = View.VISIBLE
            return
        }

        if (password.isEmpty()) {
            binding.errorMessage.text = "※パスワードを入力してください"
            binding.errorMessage.visibility = View.VISIBLE
            return
        }

        binding.btnLogin.isEnabled = false

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                Log.d("LoginActivity", "Attempting to find email for username: $username")

                // 1. Firestoreでユーザー名を検索してEmailを取得
                val querySnapshot = db.collection("users")
                    .whereEqualTo("name", username)
                    .limit(1)
                    .get()
                    .await()

                if (querySnapshot.isEmpty) {
                    withContext(Dispatchers.Main) {
                        binding.errorMessage.text = "※ユーザー名またはパスワードが正しくありません"
                        binding.errorMessage.visibility = View.VISIBLE
                    }
                    return@launch
                }

                val userDocument = querySnapshot.documents.first()
                val email = userDocument.getString("email")
                val isEmailVerifiedFirestore = userDocument.getBoolean("isEmailVerified") ?: false

                if (email == null) {
                    withContext(Dispatchers.Main) {
                        binding.errorMessage.text = "※ユーザー情報に不備があります。"
                        binding.errorMessage.visibility = View.VISIBLE
                    }
                    return@launch
                }

                // 2. Firebase Auth でログイン
                val authResult = auth.signInWithEmailAndPassword(email, password).await()
                val firebaseUser = authResult.user

                if (firebaseUser != null) {
                    Log.i("LoginActivity", "Firebase sign-in successful: ${firebaseUser.uid}")

                    // ★★★ ここから修正: ローカルDBの整合性をチェック ★★★

                    // まず、この端末に既にこのFirebase UIDを持つユーザーがいるか確認する
                    val existingLocalUser = appDatabase.userDao().getUserByFirebaseUid(firebaseUser.uid)

                    val savedUserId: Int

                    if (existingLocalUser != null) {
                        // 【ケースA: 既存端末】既にローカルにデータがある場合
                        // 既存の userId を維持したまま、情報を最新に更新する
                        Log.d("LoginActivity", "Local user found. Updating existing record.")
                        savedUserId = existingLocalUser.userId

                        val updatedUser = existingLocalUser.copy(
                            name = username,
                            email = email,
                            password = User.hashPassword(password),
                            isEmailVerified = isEmailVerifiedFirestore
                        )
                        appDatabase.userDao().update(updatedUser)
                    } else {
                        // 【ケースB: 新規端末】ローカルにデータがない場合
                        // 新しくインサートする。データ（履歴）は空の状態からスタート。
                        Log.d("LoginActivity", "No local user found. Creating new record.")

                        val newLocalUser = User(
                            firebaseUid = firebaseUser.uid,
                            name = username,
                            email = email,
                            password = User.hashPassword(password),
                            isEmailVerified = isEmailVerifiedFirestore
                        )

                        // ※注意: もし同じ端末に「同名の別人」がローカルに存在する場合、nameのUNIQUE制約でエラーになる可能性があります
                        // その場合は別のアカウントであることをユーザーに伝えるなどのハンドリングが必要ですが、
                        // ここでは一般的なログイン処理として実装します。
                        savedUserId = appDatabase.userDao().insert(newLocalUser).toInt()
                    }

                    Log.d("LoginActivity", "User setup complete. localUserId: $savedUserId")

                    // SharedPreferencesに保存
                    val sharedPrefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
                    with(sharedPrefs.edit()) {
                        putString("LOGGED_IN_FIREBASE_UID", firebaseUser.uid)
                        putInt("LOGGED_IN_USER_ID", savedUserId)
                        apply()
                    }

                    withContext(Dispatchers.Main) {
                        val intent = Intent(this@LoginActivity, HomeScreenActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                        finish()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        binding.errorMessage.text = "認証に失敗しました"
                        binding.errorMessage.visibility = View.VISIBLE
                    }
                }
            } catch (e: SQLiteConstraintException) {
                // ローカルDBの制約違反（例：既に同じ名前のユーザーが端末内に存在するがFirebaseUIDが違う場合など）
                Log.e("LoginActivity", "Database Error: ${e.message}")
                withContext(Dispatchers.Main) {
                    binding.errorMessage.text = "端末内に同じ名前の別ユーザーが存在するためログインできません。"
                    binding.errorMessage.visibility = View.VISIBLE
                }
            } catch (e: Exception) {
                Log.e("LoginActivity", "Login error: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    binding.errorMessage.text = "ログイン失敗: ${e.localizedMessage}"
                    binding.errorMessage.visibility = View.VISIBLE
                }
            } finally {
                withContext(Dispatchers.Main) {
                    binding.btnLogin.isEnabled = true
                }
            }
        }
    }
}