//MainActivity.kt
package com.example.paceface

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth // Firebase Authentication を追加
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var tokenManager: TokenManager
    private lateinit var appDatabase: AppDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        tokenManager = TokenManager(this)
        appDatabase = AppDatabase.getDatabase(this)

        lifecycleScope.launch {
            val isUserLoggedIn = checkUserLoginStatus()
            if (isUserLoggedIn) {
                navigateTo(HomeScreenActivity::class.java)
            } else {
                clearAllLoginData()
                navigateTo(SelectionScreenActivity::class.java)
            }
        }
    }

    private suspend fun checkUserLoginStatus(): Boolean = withContext(Dispatchers.IO) {
        val firebaseUser = FirebaseAuth.getInstance().currentUser // Firebase Authの状態を確認
        val sharedPrefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)

        // まずはFirebase認証済みか確認
        if (firebaseUser == null) {
            Log.d("MainActivity", "checkUserLoginStatus: No Firebase user authenticated.")
            return@withContext false
        }
        Log.d("MainActivity", "checkUserLoginStatus: Firebase user authenticated: ${firebaseUser.uid}")

        var localUserId = sharedPrefs.getInt("LOGGED_IN_USER_ID", -1)
        var localUser: User? = null

        if (localUserId != -1) {
            // SharedPreferencesにlocalUserIdがあれば、それでユーザーを検索
            localUser = appDatabase.userDao().getUserById(localUserId)
            if (localUser != null) {
                Log.d("MainActivity", "checkUserLoginStatus: Found local user by saved userId: ${localUser.userId}")
            } else {
                Log.d("MainActivity", "checkUserLoginStatus: No local user found for saved userId: $localUserId")
            }
        }

        if (localUser == null) {
            // SharedPreferencesにlocalUserIdがないか、取得したlocalUserIdでユーザーが見つからなければ
            // Firebase UIDを使ってユーザーを検索し、localUserIdをSharedPreferencesに保存し直す
            val firebaseUid = firebaseUser.uid
            localUser = appDatabase.userDao().getUserByFirebaseUid(firebaseUid)
            if (localUser != null) {
                Log.d("MainActivity", "checkUserLoginStatus: Found local user by firebaseUid: ${localUser.userId}. Updating SharedPreferences.")
                // localUserIdが見つかったのでSharedPreferencesを更新
                with(sharedPrefs.edit()) {
                    putInt("LOGGED_IN_USER_ID", localUser.userId)
                    apply()
                }
            } else {
                Log.d("MainActivity", "checkUserLoginStatus: No local user found for firebaseUid: $firebaseUid. Returning false.")
            }
        }

        // 最終的にローカルユーザーが存在すればログイン済みと判断
        return@withContext localUser != null
    }

    private suspend fun clearAllLoginData() = withContext(Dispatchers.IO) {
        tokenManager.clearTokens()

        val sharedPrefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        with(sharedPrefs.edit()) {
            remove("LOGGED_IN_USER_ID")
            remove("LOGGED_IN_FIREBASE_UID")
            apply()
        }
        Log.d("MainActivity", "clearAllLoginData: All login data cleared from SharedPreferences.")
    }

    private fun navigateTo(activityClass: Class<*>) {
        val intent = Intent(this, activityClass)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        if (activityClass == SelectionScreenActivity::class.java) {
            overridePendingTransition(0, 0)
        }
        finish()
    }

    override fun onStart() {
        super.onStart()
        // FirebaseUtils.kt から関数を呼び出すのは、この場所では適切ではありません。
        // メール認証後のFirestoreへの保存は、ユーザー登録時やメール認証完了時に実行すべきです。
        // lifecycleScope.launch {
        //     saveUserDataToFirestoreAfterEmailVerification(applicationContext)
        // }
    }
}