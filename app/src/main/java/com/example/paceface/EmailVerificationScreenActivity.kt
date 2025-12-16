//EmailVerificationScreenActivity.kt
package com.example.paceface

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.paceface.databinding.EmailVerificationScreenBinding
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
// FirebaseUtils.kt にある saveUserDataToFirestoreAfterEmailVerification 関数をインポート
// 同じパッケージ内であれば、明示的な import 文は不要です
// import com.example.paceface.saveUserDataToFirestoreAfterEmailVerification

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class EmailVerificationScreenActivity : AppCompatActivity() {

    private lateinit var binding: EmailVerificationScreenBinding
    private lateinit var auth: FirebaseAuth
    private var isVerificationCheckRunning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = EmailVerificationScreenBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = Firebase.auth

        // UserRegistrationScreenActivity から渡されたメールアドレスを設定
        val userEmail = intent.getStringExtra("USER_EMAIL")
        if (!userEmail.isNullOrEmpty()) {
            binding.tvEmail.text = userEmail
        } else {
            // メールアドレスが取得できなかった場合のフォールバック（例: ログイン中のユーザーから取得）
            binding.tvEmail.text = auth.currentUser?.email ?: "メールアドレス不明"
            // 必要に応じて、エラーメッセージを表示したり、ユーザー登録画面に戻したりすることも検討
        }

        // tvTitle および tvMessage はレイアウトに固定値で設定されているので、コードでの変更は不要です

        binding.btnResend.setOnClickListener { // XMLのID: btnResend
            sendVerificationEmail()
        }

        binding.btnLogin.setOnClickListener { // XMLのID: btnLogin
            // 「ログイン画面へ」ボタンは、認証済み確認と、認証されていない場合はログイン画面へ遷移する役割
            startEmailVerificationCheck()
        }
    }

    override fun onResume() {
        super.onResume()
        // Activityが再開されたときにもチェックを再開
        Log.d("EmailVerification", "onResume: メール認証チェックを開始します。")
        startEmailVerificationCheck()
    }

    override fun onPause() {
        super.onPause()
        // Activityが停止したときにチェックを停止し、リソースを解放
        Log.d("EmailVerification", "onPause: メール認証チェックを停止します。")
        isVerificationCheckRunning = false
    }

    private fun startEmailVerificationCheck() {
        if (isVerificationCheckRunning) {
            Log.d("EmailVerification", "メール認証チェックは既に実行中です。")
            return // 既に実行中なら何もしない
        }

        isVerificationCheckRunning = true
        lifecycleScope.launch {
            Log.d("EmailVerification", "新しいメール認証チェックコルーチンを開始しました。")
            while (isVerificationCheckRunning) { // フラグでループを制御
                val currentUser = auth.currentUser
                if (currentUser != null) {
                    try {
                        currentUser.reload().await() // 最新の認証状態を取得
                        Log.d("EmailVerification", "ユーザー情報をリロードしました。isEmailVerified: ${currentUser.isEmailVerified}");
                        Log.d("EmailVerification", "currentUser.uid: ${currentUser.uid}"); // UIDログはここ

                        if (currentUser.isEmailVerified) {
                            Log.i("EmailVerification", "メールアドレスが認証されました！Firestoreへのデータ保存を開始します。")
                            try {
                                // 認証トークンを強制的にリフレッシュ
                                val idTokenResult = currentUser.getIdToken(true).await()
                                val tokenString = idTokenResult?.token // String? としてトークンを取得

                                if (tokenString != null) {
                                    // トークン文字列が10文字未満の場合にエラーにならないように minOf を使用
                                    val logToken = tokenString.substring(0, minOf(tokenString.length, 10))
                                    Log.d("EmailVerification", "認証トークンをリフレッシュしました。トークン開始: ${logToken}...");
                                } else {
                                    Log.w("EmailVerification", "認証トークンがnullでした。");
                                }

                                // 認証トークンが更新されたことを確認してからFirestoreへのデータ保存を試みる
                                saveUserDataToFirestoreAfterEmailVerification(applicationContext)
                                navigateToHomeScreen()
                                break // ループを終了
                            } catch (tokenError: Exception) {
                                Log.e("EmailVerification", "認証トークンのリフレッシュに失敗しました: ${tokenError.message}", tokenError);
                                // エラーが発生した場合もループを継続して再試行するか、エラー処理を行う
                            }
                        } else {
                            Log.d("EmailVerification", "メールはまだ認証されていません。3秒後に再チェックします。")
                        }
                    } catch (e: Exception) {
                        Log.e("EmailVerification", "メール認証状態のリロードに失敗しました: ${e.message}", e)
                        // エラーが発生した場合もループを続けるか、特定の回数で停止するか検討
                    }
                } else {
                    Log.w("EmailVerification", "ログインしているユーザーがいません。ログイン画面へ遷移します。")
                    navigateToLoginScreen() // 例としてログイン画面へ
                    break // ループを終了
                }
                delay(3000) // 3秒ごとにチェック
            }
        }
    }

    private fun sendVerificationEmail() {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    currentUser.sendEmailVerification().await()
                    withContext(Dispatchers.Main) {
                        Snackbar.make(binding.root, "確認メールを再送しました。メールボックスを確認してください。", Snackbar.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Snackbar.make(binding.root, "確認メールの再送に失敗しました: ${e.message}", Snackbar.LENGTH_LONG).show()
                        Log.e("EmailVerification", "メール再送エラー: ${e.message}", e)
                    }
                }
            }
        } else {
            Snackbar.make(binding.root, "ユーザーがログインしていません。", Snackbar.LENGTH_LONG).show()
            navigateToLoginScreen()
        }
    }

    private fun navigateToHomeScreen() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun navigateToLoginScreen() {
        val intent = Intent(this, UserRegistrationScreenActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}