//SelectionScreenActivity.kt
package com.example.paceface

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.paceface.databinding.SelectionScreenBinding
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class SelectionScreenActivity : AppCompatActivity() {

    private lateinit var binding: SelectionScreenBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var appDatabase: AppDatabase
    private val db = Firebase.firestore

    // Google Sign-In Result Launcher
    private val googleSignInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)!!
                // Googleサインイン成功 -> Firebase認証へ
                firebaseAuthWithGoogle(account.idToken!!)
            } catch (e: ApiException) {
                Log.w("SelectionScreen", "Google sign in failed", e)
                Toast.makeText(this, "Googleログインに失敗しました。", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = SelectionScreenBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = Firebase.auth
        appDatabase = AppDatabase.getDatabase(this)

        // 1. Googleログインボタン
        binding.btnGoogleSignIn.setOnClickListener {
            initiateGoogleSignIn()
        }

        // 2. メールで新規登録ボタン
        binding.btnRegister.setOnClickListener {
            val intent = Intent(this, UserRegistrationScreenActivity::class.java)
            startActivity(intent)
        }

        // 3. メールでログインボタン
        binding.btnLogin.setOnClickListener {
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
        }
    }

    private fun initiateGoogleSignIn() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id)) // google-services.jsonの値
            .requestEmail()
            .build()
        val googleSignInClient = GoogleSignIn.getClient(this, gso)
        googleSignInLauncher.launch(googleSignInClient.signInIntent)
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val authResult = auth.signInWithCredential(credential).await()
                val firebaseUser = authResult.user
                if (firebaseUser != null) {
                    // ログイン成功時のDB保存と画面遷移処理
                    handleGoogleLoginSuccess(firebaseUser.uid, firebaseUser.email ?: "", firebaseUser.displayName ?: "No Name")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@SelectionScreenActivity, "認証エラー: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private suspend fun handleGoogleLoginSuccess(uid: String, email: String, name: String) {
        // A. ローカルDB (Room) の更新または作成
        val existingUser = appDatabase.userDao().getUserByFirebaseUid(uid)
        val userId = if (existingUser != null) {
            existingUser.userId
        } else {
            val newUser = User(
                firebaseUid = uid,
                name = name,
                email = email,
                password = "", // Googleログインのためパスワードなし
                isEmailVerified = true
            )
            appDatabase.userDao().insert(newUser).toInt()
        }

        // B. Firestoreへのユーザー情報保存
        try {
            val userData = hashMapOf(
                "uid" to uid,
                "name" to name,
                "email" to email,
                "registrationTimestamp" to com.google.firebase.Timestamp.now()
            )
            db.collection("users").document(uid).set(userData).await()
        } catch (e: Exception) {
            Log.e("SelectionScreen", "Firestore save error", e)
        }

        // C. SharedPreferencesへの保存（ログイン状態の維持）
        val sharedPrefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        with(sharedPrefs.edit()) {
            putString("LOGGED_IN_FIREBASE_UID", uid)
            putInt("LOGGED_IN_USER_ID", userId)
            apply()
        }

        // D. ホーム画面へ遷移
        withContext(Dispatchers.Main) {
            val intent = Intent(this@SelectionScreenActivity, HomeScreenActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }
}