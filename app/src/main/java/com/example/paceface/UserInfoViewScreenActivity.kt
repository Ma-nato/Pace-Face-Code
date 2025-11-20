package com.example.paceface

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity

class UserInfoViewScreenActivity : AppCompatActivity() {

    private lateinit var backButton: ImageButton
    private lateinit var homeButton: ImageButton
    private lateinit var passingButton: ImageButton
    private lateinit var historyButton: ImageButton
    private lateinit var emotionButton: ImageButton
    private lateinit var gearButton: ImageButton

    private lateinit var etUsername: EditText
    private lateinit var etEmail: EditText
    private lateinit var btnEdit: Button

    private var isEditing = false   // 編集モードフラグ

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.user_info_view_screen) // ← あなたの XML のファイル名に変更！

        setupViews()
        setupListeners()
    }

    // XML と Kotlin を紐づける
    private fun setupViews() {

        backButton = findViewById(R.id.btn_back)
        homeButton = findViewById(R.id.home_button)
        passingButton = findViewById(R.id.passing_button)
        historyButton = findViewById(R.id.history_button)
        emotionButton = findViewById(R.id.emotion_button)
        gearButton = findViewById(R.id.gear_button)

        etUsername = findViewById(R.id.et_username)
        etEmail = findViewById(R.id.et_email)
        btnEdit = findViewById(R.id.btn_edit)
    }

    // クリックイベントなどをまとめる
    private fun setupListeners() {

        // 戻るボタン
        backButton.setOnClickListener {
            finish()
        }

        // BottomNavigation のボタン
        homeButton.setOnClickListener { /*TODO*/ }
        passingButton.setOnClickListener { /*TODO*/ }
        historyButton.setOnClickListener { /*TODO*/ }
        emotionButton.setOnClickListener { /*TODO*/ }
        gearButton.setOnClickListener { /*TODO*/ }

        //編集ボタンが押下されたら変更画面に切り替わる仕組み（仮）
        // 編集ボタン
        btnEdit.setOnClickListener {
            if (!isEditing) {
                // 編集モード ON
                isEditing = true
                etUsername.isEnabled = true
                etUsername.isFocusableInTouchMode = true
                etEmail.isEnabled = true
                etEmail.isFocusableInTouchMode = true
                btnEdit.text = "変更"
            } else {
                // 編集 → 保存
                isEditing = false
                etUsername.isEnabled = false
                etEmail.isEnabled = false

//                val newName = etUsername.text.toString()
//                val newEmail = etEmail.text.toString()
                // TODO: FirestoreやDBに保存する処理を書く

                btnEdit.text = "編集"
            }
        }
    }
}
