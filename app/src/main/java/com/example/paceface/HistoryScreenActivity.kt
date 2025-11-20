package com.example.paceface

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.ImageButton
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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class HistoryScreenActivity : AppCompatActivity() {
    private lateinit var binding: HistoryScreenBinding
    private lateinit var appDatabase: AppDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = HistoryScreenBinding.inflate(layoutInflater)
        setContentView(binding.root)

        appDatabase = AppDatabase.getDatabase(this)

        val historyButton = findViewById<ImageButton>(R.id.history_button)
        historyButton?.setBackgroundColor(ContextCompat.getColor(this, R.color.selected_nav_item_bg))

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
                calendar.set(year, month, dayOfMonth, 23, 59, 59)
                val endTime = calendar.timeInMillis

                val speedDataList = appDatabase.speedDataDao().getDataForDate(startTime, endTime)
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

    private fun updateChart(data: List<SpeedData>) {
        val entries = data.map {
            val cal = Calendar.getInstance()
            cal.timeInMillis = it.timestamp
            val millisOfDay = (cal.get(Calendar.HOUR_OF_DAY) * 60 * 60 * 1000 +
                    cal.get(Calendar.MINUTE) * 60 * 1000).toFloat()
            Entry(millisOfDay, it.speed)
        }

        val dataSet = LineDataSet(entries, "速度 (km/h)")
        dataSet.color = ContextCompat.getColor(this, R.color.purple_500)
        dataSet.valueTextColor = ContextCompat.getColor(this, android.R.color.black)

        val lineData = LineData(dataSet)
        binding.lineChart.data = lineData

        binding.lineChart.xAxis.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                val hours = TimeUnit.MILLISECONDS.toHours(value.toLong())
                val minutes = TimeUnit.MILLISECONDS.toMinutes(value.toLong()) % 60
                return String.format("%02d:%02d", hours, minutes)
            }
        }

        binding.lineChart.description.text = "速度の推移"
        binding.lineChart.invalidate()
    }

    private fun insertDummyData() {
        lifecycleScope.launch {
            val today = Calendar.getInstance()
            val startOfToday = today.clone() as Calendar
            startOfToday.set(Calendar.HOUR_OF_DAY, 0)
            val endOfToday = today.clone() as Calendar
            endOfToday.set(Calendar.HOUR_OF_DAY, 23)
            val todayData = appDatabase.speedDataDao().getDataForDate(startOfToday.timeInMillis, endOfToday.timeInMillis)
            if (todayData.isNotEmpty()) return@launch

            for (i in 0..5) {
                val timestamp = today.timeInMillis - (5 - i) * 60 * 60 * 1000
                val speed = 10f + (Math.random() * 5).toFloat()
                appDatabase.speedDataDao().insert(SpeedData(timestamp = timestamp, speed = speed))
            }

            val yesterday = Calendar.getInstance()
            yesterday.add(Calendar.DAY_OF_YEAR, -1)
            for (i in 0..5) {
                val timestamp = yesterday.timeInMillis - (5 - i) * 60 * 60 * 1000
                val speed = 12f + (Math.random() * 3).toFloat()
                appDatabase.speedDataDao().insert(SpeedData(timestamp = timestamp, speed = speed))
            }
        }
    }
}
