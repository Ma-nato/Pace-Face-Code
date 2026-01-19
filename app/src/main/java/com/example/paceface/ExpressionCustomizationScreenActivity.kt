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

class ExpressionCustomizationScreenActivity : AppCompatActivity() {

    private lateinit var binding: ExpressionCustomizationScreenBinding
    private lateinit var viewModel: ExpressionViewModel
    private var selectedEmoji: ImageView? = null
    private lateinit var appDatabase: AppDatabase

    private val PREFS_NAME = "EmojiPrefs"
    private val APP_PREFS_NAME = "AppPrefs"
    private val KEY_LOGGED_IN_USER_ID = "LOGGED_IN_USER_ID"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ExpressionCustomizationScreenBinding.inflate(layoutInflater)
        setContentView(binding.root)

        appDatabase = AppDatabase.getDatabase(this)
        val sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val appPrefs = getSharedPreferences(APP_PREFS_NAME, Context.MODE_PRIVATE)
        val loggedInUserId = appPrefs.getInt(KEY_LOGGED_IN_USER_ID, -1)

        viewModel = ExpressionViewModel(appDatabase.historyDao(), sharedPreferences, loggedInUserId)

        val emojiImageViews = listOf(
            binding.emoji1, binding.emoji2, binding.emoji3,
            binding.emoji4, binding.emoji5, binding.emoji6,
            binding.emoji7
        )

        // タグの初期化を先に行う
        emojiImageViews.forEachIndexed { index, imageView ->
            imageView.tag = (index + 1).toString()
        }

        lifecycleScope.launch {
            viewModel.selectedEmojiTag.collect { tag ->
                val emojiToSelect = emojiImageViews.find { it.tag == tag } ?: binding.emoji1
                onEmojiSelected(emojiToSelect, emojiImageViews)
            }
        }

        lifecycleScope.launch {
            viewModel.isAutoChangeEnabled.collect { isEnabled ->
                binding.changeSwitch.isChecked = isEnabled
                updateUiForMode(isEnabled)
            }
        }

        emojiImageViews.forEach { emoji ->
            emoji.setOnClickListener {
                viewModel.selectEmoji(it.tag.toString())
            }
        }

        binding.changeBtn.setOnClickListener {
            viewModel.saveExpression()
            val intent = Intent(this, ExpressionChangeCompleteScreenActivity::class.java)
            startActivity(intent)
        }

        binding.changeSwitch.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setAutoChangeEnabled(isChecked)
            val message = if (isChecked) "自動変更がONになりました" else "自動変更がOFFになりました"
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }

        setupNavigation()
    }

    private fun updateUiForMode(isAutoMode: Boolean) {
        val isManualMode = !isAutoMode
        val emojiImageViews = listOf(
            binding.emoji1, binding.emoji2, binding.emoji3,
            binding.emoji4, binding.emoji5, binding.emoji6,
            binding.emoji7
        )

        // 自動変更がONのときは選択不可にする
        emojiImageViews.forEach { it.isEnabled = isManualMode }

        // 自動変更がOFFのときのみ「変更」ボタンで固定表情を保存できるようにします。
        binding.changeBtn.isEnabled = isManualMode

        val alpha = if (isManualMode) 1.0f else 0.5f
        emojiImageViews.forEach { it.alpha = alpha }
        binding.changeBtn.alpha = alpha
    }

    private fun onEmojiSelected(selectedView: ImageView, allEmojis: List<ImageView>) {
        allEmojis.forEach { emoji ->
            emoji.setBackgroundResource(R.drawable.emoji_bg)
        }
        selectedView.setBackgroundResource(R.drawable.emoji_selected_bg)
        selectedEmoji = selectedView
    }

    private fun setupNavigation() {
        NavigationUtils.setupCommonNavigation(
            this,
            ExpressionCustomizationScreenActivity::class.java,
            binding.homeButton,
            binding.passingButton,
            binding.historyButton,
            binding.emotionButton,
            binding.gearButton
        )
    }
}