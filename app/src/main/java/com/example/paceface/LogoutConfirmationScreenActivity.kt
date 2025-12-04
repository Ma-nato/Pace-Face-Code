package com.example.paceface

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class LogoutConfirmationScreenActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.logout_confirmation_screen)

        val backButton: Button = findViewById(R.id.button_back)
        val logoutButton: Button = findViewById(R.id.button_logout)

        backButton.setOnClickListener {
            finish()
        }

        logoutButton.setOnClickListener {
            val sharedPrefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
            with(sharedPrefs.edit()) {
                remove("LOGGED_IN_USER_ID")
                apply()
            }

            val intent = Intent(this, SelectionScreenActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }
}
