package com.example.paceface

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.paceface.databinding.HistoryScreenBinding
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class HistoryScreenActivity : AppCompatActivity() {
    private lateinit var binding: HistoryScreenBinding
    private lateinit var appDatabase: AppDatabase
    private var currentUserId: Int = 1 // 仮のユーザーID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = HistoryScreenBinding.inflate(layoutInflater)
        setContentView(binding.root)

        appDatabase = AppDatabase.getDatabase(this)

        binding.historyButton.setBackgroundColor(ContextCompat.getColor(this, R.color.selected_nav_item_bg))

        setupClickListeners()
        setupCalendarView()
        initChart()
        insertDummyData()
    }

    private fun initChart() {
        binding.lineChart.description.isEnabled = false
        binding.lineChart.setNoDataText("日付を選択してください")
        binding.lineChart.invalidate()
    }

    private fun setupClickListeners() {
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
            // 現在の画面なので何もしない
        }

        binding.emotionButton.setOnClickListener {
            // TODO: EmotionScreenActivity.kt を作成し、遷移を実装する
        }

        binding.gearButton.setOnClickListener {
            val intent = Intent(this, UserSettingsScreenActivity::class.java)
            startActivity(intent)
            overridePendingTransition(0, 0)
        }
    }

    private fun setupCalendarView() {
        binding.calendarView.setOnDateChangeListener { _, year, month, dayOfMonth ->
            lifecycleScope.launch {
                val calendar = Calendar.getInstance()
                calendar.set(year, month, dayOfMonth, 0, 0, 0)
                val startTime = calendar.timeInMillis
                calendar.add(Calendar.DAY_OF_YEAR, 1)
                val endTime = calendar.timeInMillis

                val speedDataList = appDatabase.hourlyAverageSpeedDao().getHourlyAverageSpeedForDate(currentUserId, startTime, endTime)
                if (speedDataList.isNotEmpty()) {
                    updateChart(speedDataList)
                } else {
                    binding.lineChart.clear()
                    binding.lineChart.setNoDataText("データがありません")
                    binding.lineChart.invalidate()
                    val date = "${year}/${month + 1}/${dayOfMonth}"
                    Toast.makeText(this@HistoryScreenActivity, "$date のデータはありません", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun updateChart(data: List<HourlyAverageSpeed>) {
        val entries = data.map {
            val cal = Calendar.getInstance()
            cal.timeInMillis = it.timestamp
            val hourOfDay = cal.get(Calendar.HOUR_OF_DAY).toFloat()
            Entry(hourOfDay, it.averageSpeed)
        }

        val dataSet = LineDataSet(entries, "平均速度 (km/h)")
        dataSet.color = ContextCompat.getColor(this, R.color.purple_500)
        dataSet.valueTextColor = ContextCompat.getColor(this, android.R.color.black)

        val lineData = LineData(dataSet)
        binding.lineChart.data = lineData

        binding.lineChart.xAxis.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                return String.format("%02.0f:00", value)
            }
        }
        binding.lineChart.xAxis.granularity = 1f
        binding.lineChart.xAxis.labelCount = 24

        binding.lineChart.description.text = "時間別 平均速度"
        binding.lineChart.invalidate()
    }

    private fun insertDummyData() {
        lifecycleScope.launch {
            val userDao = appDatabase.userDao()
            val hourlyAverageSpeedDao = appDatabase.hourlyAverageSpeedDao()

            // Insert a dummy user if not exists
            var user = userDao.getUserById(currentUserId)
            if (user == null) {
                userDao.insert(User(userId = currentUserId, email = "test@example.com", name = "Test User", password = ""))
            }

            // Insert dummy hourly speed for today and yesterday
            val calendar = Calendar.getInstance()
            for (day in 0..1) {
                if (day == 1) calendar.add(Calendar.DAY_OF_YEAR, -1)

                for (hour in 8..20) {
                    calendar.set(Calendar.HOUR_OF_DAY, hour)
                    val speed = 5f + (Math.random() * 10).toFloat()
                    hourlyAverageSpeedDao.insert(HourlyAverageSpeed(
                        userId = currentUserId,
                        timestamp = calendar.timeInMillis,
                        averageSpeed = speed
                    ))
                }
            }
        }
    }
}
