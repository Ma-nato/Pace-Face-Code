package com.example.paceface

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.paceface.databinding.HistoryScreenBinding
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.google.android.material.R as R_material
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
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

        val sharedPrefs = getSharedPreferences("AppPrefs", MODE_PRIVATE)
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
        setupChart() // グラフの初期設定

        // 初期表示として今日の日付のデータを読み込む
        updateChartForDate(Date())
    }

    private fun setupCalendar() {
        binding.calendarView.setOnDateChangeListener { _, year, month, dayOfMonth ->
            val calendar = Calendar.getInstance()
            calendar.set(year, month, dayOfMonth)
            val selectedDate = calendar.time

            // 特定の日付（2025年11月27日）が選択されたらダミーデータを挿入
            if (year == 2025 && month == Calendar.NOVEMBER && dayOfMonth == 27) {
                insertAndShowDummyData(selectedDate)
            } else {
                updateChartForDate(selectedDate)
            }
        }
    }

    private fun insertAndShowDummyData(date: Date) {
        lifecycleScope.launch {
            try {
                val cal = Calendar.getInstance().apply { time = date }
                val startOfDay = getStartOfDay(cal).timeInMillis
                val endOfDay = getEndOfDay(cal).timeInMillis

                // ダミーデータを作成
                val dummyData = listOf(
                    History(userId = currentUserId, timestamp = startOfDay + TimeUnit.HOURS.toMillis(9), walkingSpeed = 5.2f, acceleration = "", emotionId = 0),
                    History(userId = currentUserId, timestamp = startOfDay + TimeUnit.HOURS.toMillis(12) + TimeUnit.MINUTES.toMillis(30), walkingSpeed = 4.8f, acceleration = "", emotionId = 0),
                    History(userId = currentUserId, timestamp = startOfDay + TimeUnit.HOURS.toMillis(18) + TimeUnit.MINUTES.toMillis(45), walkingSpeed = 6.1f, acceleration = "", emotionId = 0)
                )

                // データベース操作をIOスレッドで実行
                withContext(Dispatchers.IO) {
                    appDatabase.historyDao().replaceDataForDate(currentUserId, startOfDay, endOfDay, dummyData)
                }

                // UI更新をメインスレッドで実行
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@HistoryScreenActivity, "2025/11/27のダミーデータを挿入しました", Toast.LENGTH_SHORT).show()
                    updateChartForDate(date)
                }

            } catch (e: Exception) {
                Log.e("HistoryScreen", "Dummy data insertion failed", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@HistoryScreenActivity, "ダミーデータの挿入に失敗しました", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun updateChartForDate(date: Date) {
        lifecycleScope.launch {
            val cal = Calendar.getInstance().apply { time = date }
            val startOfDay = getStartOfDay(cal).timeInMillis
            val endOfDay = getEndOfDay(cal).timeInMillis

            // データベースからの読み込みをIOスレッドで実行
            val historyData = withContext(Dispatchers.IO) {
                appDatabase.historyDao().getHistoryForUserOnDate(currentUserId, startOfDay, endOfDay)
            }

            // UIの更新をメインスレッドで実行
            withContext(Dispatchers.Main) {
                if (historyData.isEmpty()) {
                    binding.lineChart.clear()
                    binding.lineChart.invalidate()
                    Toast.makeText(this@HistoryScreenActivity, "この日のデータはありません", Toast.LENGTH_SHORT).show()
                } else {
                    val entries = ArrayList<Entry>()
                    historyData.forEach {
                        val timeCal = Calendar.getInstance().apply { timeInMillis = it.timestamp }
                        val hour = timeCal.get(Calendar.HOUR_OF_DAY).toFloat()
                        val minute = timeCal.get(Calendar.MINUTE).toFloat() / 60f
                        entries.add(Entry(hour + minute, it.walkingSpeed))
                    }
                    entries.sortBy { it.x }

                    val dataSet = LineDataSet(entries, "歩行速度").apply {
                        color = ContextCompat.getColor(this@HistoryScreenActivity, R_material.color.design_default_color_primary)
                        valueTextColor = Color.BLACK
                        setCircleColor(color)
                        circleRadius = 4f
                        lineWidth = 2f
                    }
                    val lineData = LineData(dataSet)
                    binding.lineChart.data = lineData
                    binding.lineChart.invalidate()
                }
            }
        }
    }

    private fun setupChart() {
        binding.lineChart.apply {
            description.isEnabled = false
            isDragEnabled = true
            setScaleEnabled(true)
            setPinchZoom(true)
            legend.isEnabled = false
            xAxis.labelRotationAngle = -45f

            // X軸のラベルをフォーマットする
            xAxis.valueFormatter = object : ValueFormatter() {
                private val format = SimpleDateFormat("HH:mm", Locale.getDefault())
                override fun getFormattedValue(value: Float): String {
                    val hours = value.toInt()
                    val minutes = ((value - hours) * 60).toInt()
                    val calendar = Calendar.getInstance().apply {
                        set(Calendar.HOUR_OF_DAY, hours)
                        set(Calendar.MINUTE, minutes)
                    }
                    return format.format(calendar.time)
                }
            }
            xAxis.setLabelCount(5, true)

            // Y軸のラベルをフォーマットする
            val leftAxis = binding.lineChart.axisLeft
            leftAxis.valueFormatter = object : ValueFormatter() {
                private val format = DecimalFormat("0.0", DecimalFormatSymbols(Locale.getDefault()))
                override fun getFormattedValue(value: Float): String {
                    return "${format.format(value)} km/h"
                }
            }
            binding.lineChart.axisRight.isEnabled = false // 右側のY軸を非表示にする
        }
    }

    private fun getStartOfDay(calendar: Calendar): Calendar {
        val newCal = calendar.clone() as Calendar
        newCal.set(Calendar.HOUR_OF_DAY, 0)
        newCal.set(Calendar.MINUTE, 0)
        newCal.set(Calendar.SECOND, 0)
        newCal.set(Calendar.MILLISECOND, 0)
        return newCal
    }

    private fun getEndOfDay(calendar: Calendar): Calendar {
        val newCal = calendar.clone() as Calendar
        newCal.set(Calendar.HOUR_OF_DAY, 23)
        newCal.set(Calendar.MINUTE, 59)
        newCal.set(Calendar.SECOND, 59)
        newCal.set(Calendar.MILLISECOND, 999)
        return newCal
    }

    private fun setupNavigation() {
        NavigationUtils.setupCommonNavigation(
            this,
            HistoryScreenActivity::class.java,
            binding.homeButton,
            binding.passingButton,
            binding.historyButton,
            binding.emotionButton,
            binding.gearButton
        )
    }
}
