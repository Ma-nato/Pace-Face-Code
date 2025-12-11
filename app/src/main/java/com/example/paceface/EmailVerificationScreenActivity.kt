package com.example.paceface

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.paceface.databinding.EmailVerificationScreenBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase

class EmailVerificationScreenActivity : AppCompatActivity() {

    private lateinit var binding: EmailVerificationScreenBinding
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = EmailVerificationScreenBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = Firebase.auth

        val email = intent.getStringExtra("USER_EMAIL")
        binding.tvEmail.text = email

        binding.btnResend.setOnClickListener {
            sendVerificationEmail()
        }

        binding.btnLogin.setOnClickListener {
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
        }
    }

    private fun sendVerificationEmail() {
        val user = auth.currentUser
        user?.sendEmailVerification()
            ?.addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Toast.makeText(baseContext, "確認メールを送信しました。", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(baseContext, "確認メールの送信に失敗しました。", Toast.LENGTH_SHORT).show()
                }
            }
    }
}
