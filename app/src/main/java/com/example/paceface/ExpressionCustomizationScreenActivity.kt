package com.example.paceface

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.paceface.databinding.ExpressionCustomizationScreenBinding

class ExpressionCustomizationScreenActivity : AppCompatActivity() {

    private lateinit var binding: ExpressionCustomizationScreenBinding
    private var selectedEmoji: ImageView? = null

    // SharedPreferencesにデータを保存するための名前とキーを定義します
    private val PREFS_NAME = "EmojiPrefs"
    private val KEY_SELECTED_EMOJI_TAG = "selectedEmojiTag"
    private val KEY_AUTO_CHANGE_ENABLED = "autoChangeEnabled"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ExpressionCustomizationScreenBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val emojiImageViews = listOf(
            binding.emoji1, binding.emoji2, binding.emoji3,
            binding.emoji4, binding.emoji5, binding.emoji6
        )

        emojiImageViews.forEachIndexed { index, imageView ->
            imageView.tag = "表情${index + 1}"
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
                    sharedPreferences.edit().putString(KEY_SELECTED_EMOJI_TAG, selectedTag).apply()
                    val intent = Intent(this, ExpressionChangeCompleteScreenActivity::class.java)
                    startActivity(intent)
                }
            } else {
                Toast.makeText(this, "変更する表情を選択してください", Toast.LENGTH_SHORT).show()
            }
        }

        // --- スイッチの状態変更リスナー ---
        binding.changeSwitch.setOnCheckedChangeListener { _, isChecked ->
            // スイッチの状態をSharedPreferencesに保存
            sharedPreferences.edit().putBoolean(KEY_AUTO_CHANGE_ENABLED, isChecked).apply()
            // UIの状態を更新
            updateUiForMode(isChecked)

            val message = if (isChecked) {
                // TODO: ここに速度に応じて表情を自動で変更するロジックを実装します。
                // 速度データをどこから取得し、どの表情にどの速度を割り当てるかの仕様が必要です。
                "自動変更がONになりました"
            } else {
                "自動変更がOFFになりました"
            }
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }

        // --- 戻るボタンの処理 ---
        binding.backButton.setOnClickListener {
            val intent = Intent(this, HomeScreenActivity::class.java)
            startActivity(intent)
        }

        // --- フッターナビゲーションの処理 ---
        binding.homeButton.setOnClickListener {
            val intent = Intent(this, HomeScreenActivity::class.java)
            startActivity(intent)
        }
        binding.passingButton.setOnClickListener {
            val intent = Intent(this, ProximityHistoryScreenActivity::class.java)
            startActivity(intent)
        }
        binding.historyButton.setOnClickListener {
            val intent = Intent(this, HistoryScreenActivity::class.java)
            startActivity(intent)
        }
        binding.emotionButton.setBackgroundColor(ContextCompat.getColor(this, R.color.selected_nav_item_bg))
        binding.emotionButton.setOnClickListener {
            // 現在の画面なので何もしない
        }
        binding.gearButton.setOnClickListener {
            val intent = Intent(this, UserSettingsScreenActivity::class.java)
            startActivity(intent)
        }

        // --- 起動時の状態復元 ---
        // 保存された表情を復元
        val savedTag = sharedPreferences.getString(KEY_SELECTED_EMOJI_TAG, null)
        val emojiToSelect = if (savedTag != null) {
            emojiImageViews.find { it.tag == savedTag } ?: binding.emoji1
        } else {
            binding.emoji1
        }
        onEmojiSelected(emojiToSelect, emojiImageViews)

        // 保存されたスイッチの状態を復元
        val isAutoChangeEnabled = sharedPreferences.getBoolean(KEY_AUTO_CHANGE_ENABLED, false)
        binding.changeSwitch.isChecked = isAutoChangeEnabled
        updateUiForMode(isAutoChangeEnabled)
    }

    /**
     * 自動変更モードに合わせてUIの有効/無効を切り替える
     * @param isAutoMode trueなら自動モード、falseなら手動モード
     */
    private fun updateUiForMode(isAutoMode: Boolean) {
        // 手動モードの場合にのみ、表情選択と変更ボタンを有効にする
        val isManualMode = !isAutoMode
        val emojiImageViews = listOf(binding.emoji1, binding.emoji2, binding.emoji3, binding.emoji4, binding.emoji5, binding.emoji6)

        emojiImageViews.forEach { it.isEnabled = isManualMode }
        binding.changeBtn.isEnabled = isManualMode

        // 無効状態のときは、見た目を灰色にする
        val alpha = if (isManualMode) 1.0f else 0.5f
        emojiImageViews.forEach { it.alpha = alpha }
        binding.changeBtn.alpha = alpha
    }

    /**
     * 表情アイコンがクリックされたときに呼び出される関数
     */
    private fun onEmojiSelected(selectedView: ImageView, allEmojis: List<ImageView>) {
        allEmojis.forEach { emoji ->
            emoji.setBackgroundResource(R.drawable.emoji_bg)
        }
        selectedView.setBackgroundResource(R.drawable.emoji_selected_bg)
        selectedEmoji = selectedView
    }
}
