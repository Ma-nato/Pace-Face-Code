package com.example.paceface

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ExpressionViewModel(
    private val historyDao: HistoryDao,
    private val sharedPreferences: SharedPreferences,
    private val userId: Int
) : ViewModel() {

    private val KEY_SELECTED_EMOJI_TAG = "selectedEmojiTag"
    private val KEY_AUTO_CHANGE_ENABLED = "autoChangeEnabled"

    private val _selectedEmojiTag = MutableStateFlow(sharedPreferences.getString(KEY_SELECTED_EMOJI_TAG, "1") ?: "1")
    val selectedEmojiTag: StateFlow<String> = _selectedEmojiTag.asStateFlow()

    private val _isAutoChangeEnabled = MutableStateFlow(sharedPreferences.getBoolean(KEY_AUTO_CHANGE_ENABLED, false))
    val isAutoChangeEnabled: StateFlow<Boolean> = _isAutoChangeEnabled.asStateFlow()

    fun selectEmoji(tag: String) {
        _selectedEmojiTag.value = tag
    }

    fun setAutoChangeEnabled(enabled: Boolean) {
        _isAutoChangeEnabled.value = enabled
        sharedPreferences.edit().putBoolean(KEY_AUTO_CHANGE_ENABLED, enabled).apply()
    }

    fun saveExpression() {
        val tag = _selectedEmojiTag.value
        sharedPreferences.edit().putString(KEY_SELECTED_EMOJI_TAG, tag).apply()

        viewModelScope.launch(Dispatchers.IO) {
            val newHistory = History(
                userId = userId,
                timestamp = System.currentTimeMillis(),
                walkingSpeed = 0.0f,
                emotionId = tag.toInt()
            )
            historyDao.insert(newHistory)
        }
    }
}
