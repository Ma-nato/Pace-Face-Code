package com.example.paceface

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.paceface.databinding.HistoryScreenBinding
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class HistoryScreenActivity : AppCompatActivity() {

    private lateinit var binding: HistoryScreenBinding
    private lateinit var appDatabase: AppDatabase
    private var currentUserId: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = HistoryScreenBinding.inflate(layoutInflater)
        setContentView(binding.root)

        appDatabase = AppDatabase.getDatabase(this)

        val sharedPrefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        currentUserId = sharedPrefs.getInt("LOGGED_IN_USER_ID", -1)

        if (currentUserId == -1) {
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
            return
        }

        setupCalendar()
        setupNavigation()

        updateChartForDate(Date())
    }

    private fun setupCalendar() {
        binding.calendarView.setOnDateChangeListener { _, year, month, dayOfMonth ->
            val calendar = Calendar.getInstance()
            calendar.set(year, month, dayOfMonth)
            val selectedDate = calendar.time
            
            if (year == 2025 && month == Calendar.NOVEMBER && dayOfMonth == 27) {
                insertAndShowDummyData(selectedDate)
            } else {
                updateChartForDate(selectedDate)
            }
        }
    }

    private fun insertAndShowDummyData(date: Date) {
        lifecycleScope.launch {
            val cal = Calendar.getInstance().apply { time = date }
            val startOfDay = getStartOfDay(cal).timeInMillis

            try {
                // ダミーデータを作成
                val dummyData = listOf(
                    History(userId = currentUserId, timestamp = startOfDay + TimeUnit.HOURS.toMillis(9), walkingSpeed = 5.2f, acceleration = "", emotionId = 1),
                    History(userId = currentUserId, timestamp = startOfDay + TimeUnit.HOURS.toMillis(12) + TimeUnit.MINUTES.toMillis(30), walkingSpeed = 4.8f, acceleration = "", emotionId = 1),
                    History(userId = currentUserId, timestamp = startOfDay + TimeUnit.HOURS.toMillis(18) + TimeUnit.MINUTES.toMillis(45), walkingSpeed = 6.1f, acceleration = "", emotionId = 1)
                )

                // 既存のデータを削除し、ダミーデータを挿入
                appDatabase.historyDao().deleteHistoryForUserOnDate(currentUserId, startOfDay, getEndOfDay(cal).timeInMillis)
                dummyData.forEach { appDatabase.historyDao().insert(it) }
                
                Toast.makeText(this@HistoryScreenActivity, "2025/11/27のダミーデータを挿入しました", Toast.LENGTH_SHORT).show()
                
                updateChartForDate(date)

            } catch (e: Exception) {
                Log.e("HistoryScreen", "Dummy data insertion failed", e)
                Toast.makeText(this@HistoryScreenActivity, "ダミーデータの挿入に失敗しました", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateChartForDate(date: Date) {
        val cal = Calendar.getInstance().apply { time = date }
        val startOfDay = getStartOfDay(cal).timeInMillis
        val endOfDay = getEndOfDay(cal).timeInMillis

        lifecycleScope.launch {
            val historyData = appDatabase.historyDao().getHistoryForUserOnDate(currentUserId, startOfDay, endOfDay)

            if (historyData.isEmpty()) {
                binding.lineChart.clear()
                binding.lineChart.invalidate()
                Toast.makeText(this@HistoryScreenActivity, "この日のデータはありません", Toast.LENGTH_SHORT).show()
                return@launch
            }

            val entries = ArrayList<Entry>()
            historyData.forEach {
                val timeCal = Calendar.getInstance().apply { timeInMillis = it.timestamp }
                val hour = timeCal.get(Calendar.HOUR_OF_DAY).toFloat()
                val minute = timeCal.get(Calendar.MINUTE).toFloat() / 60f
                entries.add(Entry(hour + minute, it.walkingSpeed))
            }
            
            entries.sortBy { it.x }

            val dataSet = LineDataSet(entries, "歩行速度 (km/h)")
            val lineData = LineData(dataSet)
            binding.lineChart.data = lineData
            binding.lineChart.invalidate()
        }
    }

    private fun getStartOfDay(calendar: Calendar): Calendar {
        return calendar.apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }

    private fun getEndOfDay(calendar: Calendar): Calendar {
        return calendar.apply {
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }
    }

    private fun setupNavigation() {
        binding.historyButton.setBackgroundColor(Color.parseColor("#33000000"))
        binding.homeButton.setOnClickListener { navigateTo(HomeScreenActivity::class.java) }
        binding.passingButton.setOnClickListener { navigateTo(ProximityHistoryScreenActivity::class.java) }
        binding.emotionButton.setOnClickListener { /* TODO */ }
        binding.gearButton.setOnClickListener { navigateTo(UserSettingsScreenActivity::class.java) }
    }

    private fun <T : AppCompatActivity> navigateTo(activityClass: Class<T>) {
        val intent = Intent(this, activityClass)
        startActivity(intent)
        overridePendingTransition(0, 0)
    }
}
