package com.example.paceface

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.paceface.databinding.UserSettingsScreenBinding

class UserSettingsScreenActivity : AppCompatActivity() {

    private lateinit var binding: UserSettingsScreenBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = UserSettingsScreenBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // --- Bottom Nav Listeners ---
        binding.gearButton.setBackgroundColor(ContextCompat.getColor(this, R.color.selected_nav_item_bg))

        binding.homeButton.setOnClickListener {
            val intent = Intent(this, HomeScreenActivity::class.java)
            startActivity(intent)
            overridePendingTransition(0, 0)
        }

        binding.passingButton.setOnClickListener {
            val intent = Intent(this, ProximityHistoryScreenActivity::class.java)
            startActivity(intent)
            overridePendingTransition(0, 0)
        }

        binding.historyButton.setOnClickListener {
            val intent = Intent(this, HistoryScreenActivity::class.java)
            startActivity(intent)
            overridePendingTransition(0, 0)
        }

        binding.gearButton.setOnClickListener {
            // Current screen, do nothing
        }

        // --- Settings Button Listeners ---

        binding.btnUserInfo.setOnClickListener {
            // ★★★ ここを修正しました！ ★★★

            // SharedPreferencesからログイン中のユーザーIDを読み出します
            val sharedPrefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
            val loggedInUserId = sharedPrefs.getInt("LOGGED_IN_USER_ID", -1) // 保存されていない場合は-1

            // UserInfoViewScreenActivityに、読み出したユーザーIDを渡します
            val intent = Intent(this, UserInfoViewScreenActivity::class.java).apply {
                putExtra(UserInfoViewScreenActivity.EXTRA_USER_ID, loggedInUserId)
            }
            startActivity(intent)
        }

        binding.btnPasswordChange.setOnClickListener {
            val intent = Intent(this, PasswordChangeScreenActivity::class.java)
            startActivity(intent)
        }

        binding.btnHelp.setOnClickListener {
            val intent = Intent(this, HelpScreenActivity::class.java)
            startActivity(intent)
        }

        binding.btnAbout.setOnClickListener {
             val intent = Intent(this, AboutScreenActivity::class.java)
             startActivity(intent)
        }

        binding.btnLogout.setOnClickListener {
            // ログアウト後はログイン画面に遷移し、戻れないように設定
            val intent = Intent(this, SelectionScreenActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
        }

        binding.btnDeleteAccount.setOnClickListener {
             val intent = Intent(this, AccountDeletionConfirmationScreenActivity::class.java)
             startActivity(intent)
        }
    }
}
