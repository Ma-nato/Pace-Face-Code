package com.example.paceface

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class UserInfoViewScreenActivity : AppCompatActivity() {

    companion object {
        // この画面を呼び出す側と受け取る側で使う「鍵」を定義します
        const val EXTRA_USER_ID = "extra_user_id"
    }

    // UI部品とデータベース関連の変数を宣言します
    private lateinit var backButton: ImageButton
    private lateinit var homeButton: ImageButton
    private lateinit var passingButton: ImageButton
    private lateinit var historyButton: ImageButton
    private lateinit var emotionButton: ImageButton
    private lateinit var gearButton: ImageButton

    private lateinit var etUsername: EditText
    private lateinit var etEmail: EditText
    private lateinit var btnEdit: Button

    private lateinit var db: AppDatabase
    private lateinit var userDao: UserDao

    private var isEditing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.user_info_view_screen)

        // UI部品を初期化します
        setupViews()
        // データベースを初期化します
        initDatabase()
        // ボタンの動作を設定します
        setupListeners()

        // --- ★★★ ここからが閲覧機能の心臓部です ★★★ ---
        // 前の画面から渡されたユーザーIDを受け取ります
        val userId = intent.getIntExtra(EXTRA_USER_ID, -1)

        if (userId == -1) {
            // ユーザーIDが渡されなかったという異常事態なので、エラーを表示して画面を閉じます
            Toast.makeText(this, "ユーザー情報の取得に失敗しました", Toast.LENGTH_LONG).show()
            finish()
        } else {
            // 正しいユーザーIDを受け取った場合、そのユーザーの情報を読み込みます
            loadAndDisplayUserData(userId)
        }
    }

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

    private fun initDatabase() {
        db = AppDatabase.getDatabase(this)
        userDao = db.userDao()
    }

    // ユーザー情報をデータベースから読み込んで表示する関数
    private fun loadAndDisplayUserData(userId: Int) {
        // Coroutineを使い、UIを固まらせることなく安全にデータベースにアクセスします
        lifecycleScope.launch(Dispatchers.IO) { // バックグラウンドで実行
            val user = userDao.getUserById(userId)

            // UIの更新は必ずメインスレッドで行います
            withContext(Dispatchers.Main) {
                if (user != null) {
                    // ユーザーが見つかった場合、情報を表示します
                    etUsername.setText(user.name)
                    etEmail.setText(user.email)
                } else {
                    // ユーザーが見つからなかった場合の処理
                    Toast.makeText(this@UserInfoViewScreenActivity, "ユーザー情報が見つかりません", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun setupListeners() {
        backButton.setOnClickListener { finish() }
        homeButton.setOnClickListener { /*TODO*/ }
        passingButton.setOnClickListener { /*TODO*/ }
        historyButton.setOnClickListener { /*TODO*/ }
        emotionButton.setOnClickListener { /*TODO*/ }
        gearButton.setOnClickListener { /*TODO*/ }

        btnEdit.setOnClickListener {
            if (!isEditing) {
                isEditing = true
                etUsername.isEnabled = true
                etUsername.isFocusableInTouchMode = true
                etEmail.isEnabled = true
                etEmail.isFocusableInTouchMode = true
                btnEdit.text = "変更"
            } else {
                isEditing = false
                etUsername.isEnabled = false
                etEmail.isEnabled = false
                // TODO: 変更された内容をデータベースに保存する処理をここに追加します
                btnEdit.text = "編集"
            }
        }
    }
}
