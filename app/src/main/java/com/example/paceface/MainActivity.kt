package com.example.paceface

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
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
                // If login check fails, ensure all data is cleared and go to selection
                clearAllLoginData()
                navigateTo(SelectionScreenActivity::class.java)
            }
        }
    }

    private suspend fun checkUserLoginStatus(): Boolean = withContext(Dispatchers.IO) {
        val token = tokenManager.getAccessToken()
        val sharedPrefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        val userId = sharedPrefs.getInt("LOGGED_IN_USER_ID", -1)

        if (token == null || userId == -1) {
            return@withContext false
        }

        // Check if the user ID from prefs actually exists in the database
        val user = appDatabase.userDao().getUserById(userId)
        return@withContext user != null
    }

    private suspend fun clearAllLoginData() = withContext(Dispatchers.IO) {
        // 1. Clear tokens from TokenManager
        tokenManager.clearTokens()

        // 2. Clear logged-in user ID from AppPrefs
        val sharedPrefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        with(sharedPrefs.edit()) {
            remove("LOGGED_IN_USER_ID")
            apply()
        }
    }

    private fun navigateTo(activityClass: Class<*>) {
        val intent = Intent(this, activityClass)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
