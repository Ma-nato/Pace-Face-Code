package com.example.paceface

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.paceface.databinding.UserSettingsScreenBinding

class UserSettingsScreenActivity : AppCompatActivity() {

    private lateinit var binding: UserSettingsScreenBinding
    private lateinit var tokenManager: TokenManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = UserSettingsScreenBinding.inflate(layoutInflater)
        setContentView(binding.root)

        tokenManager = TokenManager(this)

        // アニメーションの適用
        binding.settingsListLayout.translationY = 200f
        binding.settingsListLayout.alpha = 0f

        binding.settingsListLayout.animate()
            .translationY(0f)
            .alpha(1f)
            .setDuration(500)
            .setStartDelay(200) // 少し遅れて開始
            .start()

        // NavigationUtils を使用して共通ナビゲーションをセットアップ
        NavigationUtils.setupCommonNavigation(
            this,
            UserSettingsScreenActivity::class.java,
            binding.homeButton,
            binding.passingButton,
            binding.historyButton,
            binding.emotionButton,
            binding.gearButton
        )

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
            val intent = Intent(this, LogoutConfirmationScreenActivity::class.java)
            startActivity(intent)
        }

        binding.btnDeleteAccount.setOnClickListener {
             val intent = Intent(this, AccountDeletionConfirmationScreenActivity::class.java)
             startActivity(intent)
        }
    }
}
