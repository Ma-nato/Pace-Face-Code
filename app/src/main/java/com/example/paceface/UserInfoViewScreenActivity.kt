package com.example.paceface

import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class UserInfoViewScreenActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_USER_ID = "extra_user_id"
    }

    private lateinit var backButton: ImageButton
    private lateinit var etUsername: EditText
    private lateinit var etEmail: EditText
    private lateinit var btnEdit: Button

    private lateinit var db: AppDatabase
    private lateinit var userDao: UserDao

    private var currentUser: User? = null
    private var isEditing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.user_info_view_screen)

        setupViews()
        initDatabase()
        setupListeners()

        val userId = intent.getIntExtra(EXTRA_USER_ID, -1)
        if (userId == -1) {
            showErrorAndFinish("ユーザー情報の取得に失敗しました")
        } else {
            loadAndDisplayUserData(userId)
        }
    }

    private fun setupViews() {
        backButton = findViewById(R.id.btn_back)
        etUsername = findViewById(R.id.et_username)
        etEmail = findViewById(R.id.et_email)
        btnEdit = findViewById(R.id.btn_edit)
    }

    private fun initDatabase() {
        db = AppDatabase.getDatabase(this)
        userDao = db.userDao()
    }

    private fun setupListeners() {
        backButton.setOnClickListener { finish() }

        btnEdit.setOnClickListener {
            if (!isEditing) {
                setEditMode(true)
            } else {
                showConfirmationDialog()
            }
        }
    }

    private fun setEditMode(isEditing: Boolean) {
        this.isEditing = isEditing
        etUsername.isEnabled = isEditing
        etUsername.isFocusable = isEditing
        etUsername.isFocusableInTouchMode = isEditing
        etEmail.isEnabled = isEditing
        etEmail.isFocusable = isEditing
        etEmail.isFocusableInTouchMode = isEditing
        btnEdit.text = if (isEditing) "変更" else "編集"
        if (isEditing) {
            etUsername.requestFocus()
        }
    }

    private fun loadAndDisplayUserData(userId: Int) {
        lifecycleScope.launch(Dispatchers.IO) {
            currentUser = userDao.getUserById(userId)
            withContext(Dispatchers.Main) {
                currentUser?.let {
                    etUsername.setText(it.name)
                    etEmail.setText(it.email)
                } ?: showErrorAndFinish("ユーザー情報が見つかりません")
            }
        }
    }

    private fun showConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle("変更の確認")
            .setMessage("入力された内容で変更を保存しますか？")
            .setPositiveButton("はい") { _, _ ->
                saveChanges()
            }
            .setNegativeButton("いいえ", null)
            .show()
    }

    // ★★★ この関数を修正しました！ ★★★
    private fun saveChanges() {
        val newUsername = etUsername.text.toString()
        val newEmail = etEmail.text.toString()

        currentUser?.let { user ->
            val updatedUser = user.copy(name = newUsername, email = newEmail)

            lifecycleScope.launch(Dispatchers.IO) {
                userDao.update(updatedUser)
                
                withContext(Dispatchers.Main) {
                    // Toastメッセージの代わりに、完了画面に遷移します
                    val intent = Intent(this@UserInfoViewScreenActivity, ExpressionChangeCompleteScreenActivity::class.java)
                    startActivity(intent)
                }
            }
        }
    }
    
    private fun showErrorAndFinish(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        finish()
    }
}
