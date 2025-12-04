package com.example.paceface

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

class MainActivity : AppCompatActivity() {

    private lateinit var tokenManager: TokenManager

    override fun onCreate(savedInstanceState: Bundle?) {
        // Handle the splash screen transition.
        installSplashScreen()

        super.onCreate(savedInstanceState)

        tokenManager = TokenManager(this)

        if (tokenManager.getAccessToken() != null) {
            // User is logged in, go to home screen
            val intent = Intent(this, HomeScreenActivity::class.java)
            startActivity(intent)
        } else {
            // User is not logged in, go to selection screen
            val intent = Intent(this, SelectionScreenActivity::class.java)
            startActivity(intent)
        }
        finish()
    }
}
