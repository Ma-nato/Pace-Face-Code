//LogoutConfirmationScreenActivity.kt
package com.example.paceface

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

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
            // Firebaseからサインアウト
            FirebaseAuth.getInstance().signOut()

            val sharedPrefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
            with(sharedPrefs.edit()) {
                remove("LOGGED_IN_USER_ID")
                remove("LOGGED_IN_FIREBASE_UID") // Firebase UIDも削除
                apply()
            }

            val intent = Intent(this, SelectionScreenActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }
}
