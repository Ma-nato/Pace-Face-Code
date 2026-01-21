//UserInfoViewScreenActivity.kt
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
import com.example.paceface.databinding.UserInfoViewScreenBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class UserInfoViewScreenActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_USER_ID = "extra_user_id"
    }

    private lateinit var binding: UserInfoViewScreenBinding

    private lateinit var db: AppDatabase
    private lateinit var userDao: UserDao
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private var currentUser: User? = null
    private var isEditing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = UserInfoViewScreenBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initDatabase()
        setupListeners()

        val userId = intent.getIntExtra(EXTRA_USER_ID, -1)
        if (userId == -1) {
            showErrorAndFinish("ユーザー情報の取得に失敗しました")
        } else {
            loadAndDisplayUserData(userId)
        }

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
        binding.backButton.setOnClickListener { finish() }

        binding.btnEdit.setOnClickListener {
            if (!isEditing) {
                setEditMode(true)
            } else {
                showConfirmationDialog()
            }
        }
    }

    private fun setEditMode(isEditing: Boolean) {
        this.isEditing = isEditing
        binding.etUsername.isEnabled = isEditing
        binding.etUsername.isFocusable = isEditing
        binding.etUsername.isFocusableInTouchMode = isEditing
        binding.etEmail.isEnabled = isEditing
        binding.etEmail.isFocusable = isEditing
        binding.etEmail.isFocusableInTouchMode = isEditing
        binding.btnEdit.text = if (isEditing) "保存" else "編集"
        if (isEditing) {
            binding.etUsername.requestFocus()
        }
    }

    private fun loadAndDisplayUserData(userId: Int) {
        lifecycleScope.launch(Dispatchers.IO) {
            currentUser = userDao.getUserById(userId)
            withContext(Dispatchers.Main) {
                currentUser?.let {
                    binding.etUsername.setText(it.name)
                    binding.etEmail.setText(it.email)
                } ?: showErrorAndFinish("ユーザー情報が見つかりません")
            }
        }
    }

    private fun showConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle("変更の確認")
            .setMessage("入力された内容で変更を保存しますか？\n(メールアドレスの変更には再認証が必要な場合があります)")
            .setPositiveButton("はい") { _, _ ->
                saveChanges()
            }
            .setNegativeButton("いいえ", null)
            .show()
    }

    private fun saveChanges() {
        val newUsername = binding.etUsername.text.toString().trim()
        val newEmail = binding.etEmail.text.toString().trim()

        if (newUsername.isEmpty() || newEmail.isEmpty()) {
            Toast.makeText(this, "ユーザー名とメールアドレスを入力してください", Toast.LENGTH_SHORT).show()
            return
        }

        currentUser?.let { user ->
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    // 1. Firebase Auth のメールアドレス更新 (必要な場合)
                    val firebaseUser = auth.currentUser
                    if (firebaseUser != null && firebaseUser.email != newEmail) {
                        firebaseUser.updateEmail(newEmail).await()
                    }

                    // 2. Firestore のユーザー情報更新
                    if (firebaseUser != null) {
                        val updates = hashMapOf<String, Any>(
                            "name" to newUsername,
                            "email" to newEmail
                        )
                        firestore.collection("users").document(firebaseUser.uid)
                            .update(updates)
                            .await()
                    }

                    // 3. ローカルDBの更新
                    val updatedUser = user.copy(name = newUsername, email = newEmail)
                    userDao.update(updatedUser)

                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@UserInfoViewScreenActivity, "ユーザー情報を更新しました", Toast.LENGTH_SHORT).show()
                        setEditMode(false)
                        // 完了画面へ遷移
                        val intent = Intent(this@UserInfoViewScreenActivity, ExpressionChangeCompleteScreenActivity::class.java)
                        startActivity(intent)
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@UserInfoViewScreenActivity, "更新に失敗しました: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun showErrorAndFinish(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        finish()
    }
}