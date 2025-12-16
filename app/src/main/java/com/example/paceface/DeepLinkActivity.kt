//DeepLinkActivity.kt
package com.example.paceface

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DeepLinkActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var appDatabase: AppDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = Firebase.auth
        appDatabase = AppDatabase.getDatabase(this)

        val intent = intent
        val deepLink = intent.data

        if (deepLink != null && auth.isSignInWithEmailLink(deepLink.toString())) {
            val sharedPrefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
            val email = sharedPrefs.getString("EMAIL_FOR_VERIFICATION", null)

            if (email != null) {
                lifecycleScope.launch(Dispatchers.IO) {
                    val user = appDatabase.userDao().getUserByEmail(email)
                    if (user != null) {
                        val updatedUser = user.copy(isEmailVerified = true)
                        appDatabase.userDao().update(updatedUser)

                        withContext(Dispatchers.Main) {
                            Toast.makeText(applicationContext, "メールアドレスが確認できました。", Toast.LENGTH_SHORT).show()
                            // Clear the stored email
                            with(sharedPrefs.edit()) {
                                remove("EMAIL_FOR_VERIFICATION")
                                apply()
                            }
                        }
                    }
                    // Redirect to Device Connection Guide Screen (変更)
                    val nextIntent = Intent(this@DeepLinkActivity, DeviceConnectionGuideScreenActivity::class.java)
                    nextIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(nextIntent)
                    finish()
                }
            } else {
                // Handle case where email is not available
                Toast.makeText(this, "認証エラーが発生しました。", Toast.LENGTH_LONG).show()
                val nextIntent = Intent(this, DeviceConnectionGuideScreenActivity::class.java)
                startActivity(nextIntent)
                finish()
            }
        } else {
            // Fallback to Device Connection Guide for other intents (変更)
            val nextIntent = Intent(this, DeviceConnectionGuideScreenActivity::class.java)
            startActivity(nextIntent)
            finish()
        }
    }
}
