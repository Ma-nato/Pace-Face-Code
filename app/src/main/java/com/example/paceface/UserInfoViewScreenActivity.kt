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
import com.example.paceface.databinding.UserInfoViewScreenBinding // Bindingクラスをインポート
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class UserInfoViewScreenActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_USER_ID = "extra_user_id"
    }

    private lateinit var binding: UserInfoViewScreenBinding // Bindingクラスを使用

    private lateinit var db: AppDatabase
    private lateinit var userDao: UserDao

    private var currentUser: User? = null
    private var isEditing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = UserInfoViewScreenBinding.inflate(layoutInflater) // Bindingクラスを初期化
        setContentView(binding.root)

        initDatabase()
        setupListeners()

        val userId = intent.getIntExtra(EXTRA_USER_ID, -1)
        if (userId == -1) {
            showErrorAndFinish("ユーザー情報の取得に失敗しました")
        } else {
            loadAndDisplayUserData(userId)
        }

        // NavigationUtils を使用して共通ナビゲーションをセットアップ
        // このActivityはナビゲーションバーの主要な画面ではないため、どれもハイライトされない
        NavigationUtils.setupCommonNavigation(
            this,
            UserInfoViewScreenActivity::class.java,
            binding.homeButton,
            binding.passingButton,
            binding.historyButton,
            binding.emotionButton,
            binding.gearButton
        )
    }

    private fun initDatabase() {
        db = AppDatabase.getDatabase(this)
        userDao = db.userDao()
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener { finish() } // bindingを使用

        binding.btnEdit.setOnClickListener { // bindingを使用
            if (!isEditing) {
                setEditMode(true)
            } else {
                showConfirmationDialog()
            }
        }
    }

    private fun setEditMode(isEditing: Boolean) {
        this.isEditing = isEditing
        binding.etUsername.isEnabled = isEditing // bindingを使用
        binding.etUsername.isFocusable = isEditing // bindingを使用
        binding.etUsername.isFocusableInTouchMode = isEditing // bindingを使用
        binding.etEmail.isEnabled = isEditing // bindingを使用
        binding.etEmail.isFocusable = isEditing // bindingを使用
        binding.etEmail.isFocusableInTouchMode = isEditing // bindingを使用
        binding.btnEdit.text = if (isEditing) "変更" else "編集" // bindingを使用
        if (isEditing) {
            binding.etUsername.requestFocus() // bindingを使用
        }
    }

    private fun loadAndDisplayUserData(userId: Int) {
        lifecycleScope.launch(Dispatchers.IO) {
            currentUser = userDao.getUserById(userId)
            withContext(Dispatchers.Main) {
                currentUser?.let {
                    binding.etUsername.setText(it.name) // bindingを使用
                    binding.etEmail.setText(it.email) // bindingを使用
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

    private fun saveChanges() {
        val newUsername = binding.etUsername.text.toString() // bindingを使用
        val newEmail = binding.etEmail.text.toString() // bindingを使用

        currentUser?.let { user ->
            val updatedUser = user.copy(name = newUsername, email = newEmail)

            lifecycleScope.launch(Dispatchers.IO) {
                userDao.update(updatedUser)
                
                withContext(Dispatchers.Main) {
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