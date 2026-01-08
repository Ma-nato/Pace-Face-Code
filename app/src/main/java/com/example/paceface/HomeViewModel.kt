package com.example.paceface

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar

class HomeViewModel(
    private val historyDao: HistoryDao,
    private val speedRuleDao: SpeedRuleDao,
    private val userId: Int
) : ViewModel() {

    private val _currentSpeed = MutableStateFlow(0f)
    val currentSpeed: StateFlow<Float> = _currentSpeed.asStateFlow()

    private val _historyData = MutableStateFlow<List<History>>(emptyList())
    val historyData: StateFlow<List<History>> = _historyData.asStateFlow()

    // ルール更新を通知するためのイベント
    private val _ruleUpdatedEvent = MutableStateFlow<String?>(null)
    val ruleUpdatedEvent: StateFlow<String?> = _ruleUpdatedEvent.asStateFlow()

    init {
        observeHistory()
        checkAndGeneratePersonalizedRules(isInitial = true)
    }

    fun clearUpdateEvent() {
        _ruleUpdatedEvent.value = null
    }

    fun updateSpeed(speed: Float) {
        _currentSpeed.value = speed
    }

    private fun observeHistory() {
        viewModelScope.launch {
            val now = Calendar.getInstance()
            val windowStart = (now.clone() as Calendar).apply { add(Calendar.MINUTE, -10) }.timeInMillis
            val windowEnd = (now.clone() as Calendar).apply { add(Calendar.MINUTE, 10) }.timeInMillis

            historyDao.getHistoryFlowForUserOnDate(userId, windowStart, windowEnd).collect { data ->
                _historyData.value = data
                // データが100件増えるたびにルールを再評価
                if (data.size > 0 && data.size % 100 == 0) {
                    checkAndGeneratePersonalizedRules()
                }
            }
        }
    }

    private fun checkAndGeneratePersonalizedRules(isInitial: Boolean = false) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            // 1. 異常値の除外 (時速 0.5km 未満と 10km 以上を除外)
            val validSpeeds = historyDao.getAllWalkingSpeeds(userId)
                .filter { it in 0.5f..10.0f }
                .sorted()

            // 2. ハイブリッド学習ロジック
            val defaultBaseSpeed = 4.0f
            val baseSpeed = if (validSpeeds.size < 10) {
                defaultBaseSpeed
            } else {
                val medianSpeed = validSpeeds[validSpeeds.size / 2]
                val weight = (validSpeeds.size / 100f).coerceAtMost(1.0f)
                (medianSpeed * weight) + (defaultBaseSpeed * (1.0f - weight))
            }

            // 3. 既存ルールとの比較（大幅な変更がない場合は更新しない）
            val currentRules = speedRuleDao.getSpeedRulesForUser(userId)
            val currentNormalRule = currentRules.find { it.emotionId == 1 }
            if (currentNormalRule != null && !isInitial) {
                val currentBase = (currentNormalRule.minSpeed + currentNormalRule.maxSpeed) / 2
                if (Math.abs(currentBase - baseSpeed) < 0.2f) return@launch
            }

            // 4. パーソナライズされたルールの生成
            val newRules = listOf(
                SpeedRule(userId = userId, minSpeed = 0f, maxSpeed = baseSpeed * 0.7f, emotionId = 5),
                SpeedRule(userId = userId, minSpeed = baseSpeed * 0.7f, maxSpeed = baseSpeed * 0.9f, emotionId = 4),
                SpeedRule(userId = userId, minSpeed = baseSpeed * 0.9f, maxSpeed = baseSpeed * 1.1f, emotionId = 1),
                SpeedRule(userId = userId, minSpeed = baseSpeed * 1.1f, maxSpeed = baseSpeed * 1.3f, emotionId = 2),
                SpeedRule(userId = userId, minSpeed = baseSpeed * 1.3f, maxSpeed = Float.MAX_VALUE, emotionId = 3)
            )

            speedRuleDao.deleteRulesForUser(userId)
            speedRuleDao.insertAll(newRules)

            if (!isInitial) {
                _ruleUpdatedEvent.value = "歩行スタイルを学習し、速度ルールを最適化しました（基準: ${String.format("%.1f", baseSpeed)} km/h）"
            }
        }
    }
}
