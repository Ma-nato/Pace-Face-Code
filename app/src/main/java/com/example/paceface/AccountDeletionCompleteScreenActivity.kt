//AccountDeletionCompleteScreenActivity.kt
package com.example.paceface

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.paceface.databinding.AccountDeletionCompleteScreenBinding

class AccountDeletionCompleteScreenActivity : AppCompatActivity() {

    private lateinit var binding: AccountDeletionCompleteScreenBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // View Bindingを使ってレイアウトをインフレートし、画面に設定します
        binding = AccountDeletionCompleteScreenBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // bindingオブジェクトを通して、安全にボタンを参照し、クリックイベントを設定します
        binding.btnOk.setOnClickListener {
            val intent = Intent(this, SelectionScreenActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(intent)
        }
    }
}
