//ExpressionCustomizationScreenActivity.kt
package com.example.paceface

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.paceface.databinding.ExpressionCustomizationScreenBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// ★★★ 外部クラスのインポート（仮定。実際のクラス名に合わせてください） ★★★
// NavigationUtils クラスはプロジェクト内にあると仮定します。
// 各ナビゲーション先のActivityもインポートします。
import com.example.paceface.HomeScreenActivity
import com.example.paceface.HistoryScreenActivity
import com.example.paceface.ProximityHistoryScreenActivity // PassingButtonの遷移先
import com.example.paceface.UserSettingsScreenActivity // GearButtonの遷移先
// ExpressionChangeCompleteScreenActivity も必要
import com.example.paceface.ExpressionChangeCompleteScreenActivity
// ★★★

class ExpressionCustomizationScreenActivity : AppCompatActivity() {

    private lateinit var binding: ExpressionCustomizationScreenBinding
    private var selectedEmoji: ImageView? = null
    private lateinit var appDatabase: AppDatabase

    // SharedPreferencesにデータを保存するための名前とキーを定義します
    private val PREFS_NAME = "EmojiPrefs"
    private val KEY_SELECTED_EMOJI_TAG = "selectedEmojiTag"
    private val KEY_AUTO_CHANGE_ENABLED = "autoChangeEnabled"
    private val APP_PREFS_NAME = "AppPrefs"
    private val KEY_LOGGED_IN_USER_ID = "LOGGED_IN_USER_ID"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ExpressionCustomizationScreenBinding.inflate(layoutInflater)
        setContentView(binding.root)

        appDatabase = AppDatabase.getDatabase(this)
        val sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val emojiImageViews = listOf(
            binding.emoji1, binding.emoji2, binding.emoji3,
            binding.emoji4, binding.emoji5, binding.emoji6
        )

        // 各ImageViewに、どの絵文字かを識別するための「タグ」として、対応するemotionIdを設定します
        emojiImageViews.forEachIndexed { index, imageView ->
            imageView.tag = (index + 1).toString() // "1", "2", ...
        }

        emojiImageViews.forEach { emoji ->
            emoji.setOnClickListener {
                onEmojiSelected(it as ImageView, emojiImageViews)
            }
        }

        binding.changeBtn.setOnClickListener {
            if (selectedEmoji != null) {
                val selectedTag = selectedEmoji?.tag?.toString()
                if (selectedTag != null) {
                    // UI設定をSharedPreferencesに保存（タグはemotionIdなので文字列として保存）
                    sharedPreferences.edit().putString(KEY_SELECTED_EMOJI_TAG, selectedTag).apply()
                    // DBに表情の変更履歴を保存
                    saveHistoryToDatabase(selectedTag.toInt())

                    val intent = Intent(this, ExpressionChangeCompleteScreenActivity::class.java)
                    // アニメーションを統一するため、遷移先にアニメーションを禁止するフラグを設定するのが一般的ですが、
                    // ここでは完了画面への一時的な遷移のため、このままとします。
                    startActivity(intent)
                }
            } else {
                Toast.makeText(this, "変更する表情を選択してください", Toast.LENGTH_SHORT).show()
            }
        }

        binding.changeSwitch.setOnCheckedChangeListener { _, isChecked ->
            // スイッチの状態をSharedPreferencesに保存
            sharedPreferences.edit().putBoolean(KEY_AUTO_CHANGE_ENABLED, isChecked).apply()
            // UIの状態を更新
            updateUiForMode(isChecked)

            val message = if (isChecked) {
                "自動変更がONになりました"
            } else {
                "自動変更がOFFになりました"
            }
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }

        setupNavigation() //追加したセットアップ関数を呼び出す
        // --- 起動時の状態復元 ---
        val savedTag = sharedPreferences.getString(KEY_SELECTED_EMOJI_TAG, "1") // デフォルトは"1"
        val emojiToSelect = emojiImageViews.find { it.tag == savedTag } ?: binding.emoji1
        onEmojiSelected(emojiToSelect, emojiImageViews)

        val isAutoChangeEnabled = sharedPreferences.getBoolean(KEY_AUTO_CHANGE_ENABLED, false)
        binding.changeSwitch.isChecked = isAutoChangeEnabled
        updateUiForMode(isAutoChangeEnabled)
    }

    /**
     * 選択された表情の履歴をデータベースに保存する
     */
    private fun saveHistoryToDatabase(emotionId: Int) {
        val appPrefs = getSharedPreferences(APP_PREFS_NAME, Context.MODE_PRIVATE)
        val loggedInUserId = appPrefs.getInt(KEY_LOGGED_IN_USER_ID, -1)

        if (loggedInUserId == -1) {
            Toast.makeText(this, "エラー: ログイン中のユーザーが見つかりません", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val newHistory = History(
                historyId = 0, // 0で挿入すると自動採番
                userId = loggedInUserId,
                timestamp = System.currentTimeMillis(),
                walkingSpeed = 0.0f, // 仮の値
                acceleration = "",      // 仮の値
                emotionId = emotionId
            )
            appDatabase.historyDao().insert(newHistory)
        }
    }

    private fun updateUiForMode(isAutoMode: Boolean) {
        val isManualMode = !isAutoMode
        val emojiImageViews = listOf(binding.emoji1, binding.emoji2, binding.emoji3, binding.emoji4, binding.emoji5, binding.emoji6)

        emojiImageViews.forEach { it.isEnabled = isManualMode }
        binding.changeBtn.isEnabled = isManualMode

        val alpha = if (isManualMode) 1.0f else 0.5f
        emojiImageViews.forEach { it.alpha = alpha }
        binding.changeBtn.alpha = alpha
    }

    private fun onEmojiSelected(selectedView: ImageView, allEmojis: List<ImageView>) {
        allEmojis.forEach { emoji ->
            // R.drawable.emoji_bg と R.drawable.emoji_selected_bg はプロジェクト内に存在すると仮定
            emoji.setBackgroundResource(R.drawable.emoji_bg)
        }
        selectedView.setBackgroundResource(R.drawable.emoji_selected_bg)
        selectedEmoji = selectedView
    }
    private fun setupNavigation() {
        // NavigationUtils クラスはプロジェクト内にあり、アニメーション抑制のロジックを持つと仮定
        NavigationUtils.setupCommonNavigation(
            this,
            ExpressionCustomizationScreenActivity::class.java, // 現在のアクティビティ
            binding.homeButton,
            binding.passingButton,
            binding.historyButton,
            binding.emotionButton, // 自分自身のボタン
            binding.gearButton
        )
    }
}