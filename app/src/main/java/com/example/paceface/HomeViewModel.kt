package com.example.paceface

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar

class HomeViewModel(private val historyDao: HistoryDao, private val userId: Int) : ViewModel() {

    private val _currentSpeed = MutableStateFlow(0f)
    val currentSpeed: StateFlow<Float> = _currentSpeed.asStateFlow()

    private val _historyData = MutableStateFlow<List<History>>(emptyList())
    val historyData: StateFlow<List<History>> = _historyData.asStateFlow()

    init {
        observeHistory()
    }

    fun updateSpeed(speed: Float) {
        _currentSpeed.value = speed
    }

    private fun observeHistory() {
        viewModelScope.launch {
            val now = Calendar.getInstance()
            val windowStart = (now.clone() as Calendar).apply { add(Calendar.MINUTE, -10) }.timeInMillis
            val windowEnd = (now.clone() as Calendar).apply { add(Calendar.MINUTE, 10) }.timeInMillis

            historyDao.getHistoryFlowForUserOnDate(userId, windowStart, windowEnd).collect {
                _historyData.value = it
            }
        }
    }
}
