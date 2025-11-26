package com.example.paceface

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.paceface.databinding.AccountDeletionCompleteScreenBinding // View Bindingクラスをインポート

class AccountDeletionCompleteScreenActivity : AppCompatActivity() {

    // View Binding用の変数を宣言
    private lateinit var binding: AccountDeletionCompleteScreenBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // View Bindingを使ってレイアウトをインフレート（準備）
        binding = AccountDeletionCompleteScreenBinding.inflate(layoutInflater)
        // 生成されたbindingのルートビューを画面に設定
        setContentView(binding.root)

        // bindingオブジェクトを通して、ID 'btn_ok' のボタンを参照
        binding.btnOk.setOnClickListener {
            // これまでの画面の履歴をすべて消去し、
            // アプリの最初の選択画面に戻るための準備をします
            val intent = Intent(this, SelectionScreenActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            // 新しい画面を開始します（選択画面に移動）
            startActivity(intent)
        }
    }
}
