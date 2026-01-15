//HistoryScreenActivity.kt
package com.example.paceface

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.paceface.databinding.HistoryScreenBinding
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.PercentFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.utils.ColorTemplate
import com.google.android.material.R as R_material
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Calendar
import java.util.Date
import java.util.Locale

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
        setupCharts() // Setup for both charts

        // Initial load for today's data
        updateChartsForDate(Date())
    }

    private fun setupCalendar() {
        binding.calendarView.setOnDateChangeListener { _, year, month, dayOfMonth ->
            val calendar = Calendar.getInstance()
            calendar.set(year, month, dayOfMonth)
            val selectedDate = calendar.time
            updateChartsForDate(selectedDate)
        }
    }

    private fun updateChartsForDate(date: Date) {
        lifecycleScope.launch {
            val cal = Calendar.getInstance().apply { time = date }
            val startOfDay = getStartOfDay(cal).timeInMillis
            val endOfDay = getEndOfDay(cal).timeInMillis

            val historyData = withContext(Dispatchers.IO) {
                appDatabase.historyDao().getHistoryForUserOnDate(currentUserId, startOfDay, endOfDay)
            }

            withContext(Dispatchers.Main) {
                if (historyData.isEmpty()) {
                    binding.lineChart.clear()
                    binding.lineChart.invalidate()
                    binding.pieChart.clear()
                    binding.pieChart.invalidate()
                    Toast.makeText(this@HistoryScreenActivity, "この日のデータはありません", Toast.LENGTH_SHORT).show()
                } else {
                    updateLineChart(historyData)
                    updatePieChart(historyData)
                }
            }
        }
    }

    private fun updateLineChart(historyData: List<History>) {
        val hourlyData = historyData.groupBy {
            val timeCal = Calendar.getInstance().apply { timeInMillis = it.timestamp }
            timeCal.get(Calendar.HOUR_OF_DAY)
        }.map { (hour, hourlyHistory) ->
            val averageSpeed = hourlyHistory.map { it.walkingSpeed }.average().toFloat()
            Entry(hour.toFloat(), averageSpeed)
        }.sortedBy { it.x }

        val dataSet = LineDataSet(ArrayList(hourlyData), "歩行速度").apply {
            color = ContextCompat.getColor(this@HistoryScreenActivity, R_material.color.design_default_color_primary)
            valueTextColor = Color.BLACK
            setCircleColor(color)
            circleRadius = 4f
            lineWidth = 2f
        }
        binding.lineChart.data = LineData(dataSet)
        binding.lineChart.invalidate()
    }

    private fun updatePieChart(historyData: List<History>) {
        // 1から6までのすべての表情IDを集計対象にする
        val emotionCounts = historyData.groupingBy { it.emotionId }.eachCount()
            .filterKeys { it in 1..6 }

        if (emotionCounts.isEmpty()) {
            binding.pieChart.clear()
            binding.pieChart.invalidate()
            return
        }

        // データの順序を固定（1:通常, 2:困惑, 3:焦り, 4:笑顔, 5:悲しみ, 6:怒り）
        val sortedEmotionIds = emotionCounts.keys.sorted()
        val pieEntries = sortedEmotionIds.map { emotionId ->
            PieEntry(emotionCounts[emotionId]!!.toFloat(), getEmotionLabel(emotionId))
        }

        val dataSet = PieDataSet(pieEntries, "").apply {
            // 表情IDに基づいた色分けを設定
            colors = sortedEmotionIds.map { getEmotionColor(it) }
            setDrawValues(true)
        }

        val pieData = PieData(dataSet).apply {
            setValueFormatter(PercentFormatter(binding.pieChart))
            setValueTextSize(12f)
            setValueTextColor(Color.BLACK)
        }

        binding.pieChart.data = pieData
        binding.pieChart.invalidate()
    }

    private fun getEmotionLabel(emotionId: Int): String {
        return when (emotionId) {
            1 -> "通常"
            2 -> "困惑"
            3 -> "焦り"
            4 -> "笑顔"
            5 -> "悲しみ"
            6 -> "怒り"
            else -> "不明"
        }
    }

    private fun getEmotionColor(emotionId: Int): Int {
        return when (emotionId) {
            1 -> Color.parseColor("#A0AEC0") // 通常: グレー
            2 -> Color.parseColor("#F6AD55") // 困惑: オレンジ
            3 -> Color.parseColor("#FC8181") // 焦り: 薄い赤
            4 -> Color.parseColor("#68D391") // 笑顔: 緑
            5 -> Color.parseColor("#63B3ED") // 悲しみ: 青
            6 -> Color.parseColor("#E53E3E") // 怒り: 赤
            else -> Color.GRAY
        }
    }

    private fun setupCharts() {
        // Line Chart Setup
        binding.lineChart.apply {
            description.isEnabled = false
            isDragEnabled = true
            setScaleEnabled(true)
            setPinchZoom(true)
            legend.isEnabled = false
            xAxis.labelRotationAngle = -45f

            xAxis.valueFormatter = object : ValueFormatter() {
                private val format = DecimalFormat("00時", DecimalFormatSymbols(Locale.getDefault()))
                override fun getFormattedValue(value: Float): String {
                    return format.format(value)
                }
            }
            xAxis.setLabelCount(5, true)

            val leftAxis = axisLeft
            leftAxis.valueFormatter = object : ValueFormatter() {
                private val format = DecimalFormat("0.0", DecimalFormatSymbols(Locale.getDefault()))
                override fun getFormattedValue(value: Float): String {
                    return "${format.format(value)} km/h"
                }
            }
            axisRight.isEnabled = false
        }

        // Pie Chart Setup
        binding.pieChart.apply {
            description.isEnabled = false
            isDrawHoleEnabled = true
            setDrawEntryLabels(true)
            setEntryLabelColor(Color.BLACK)
            holeRadius = 50f
            transparentCircleRadius = 55f
            setUsePercentValues(true)
            legend.isEnabled = true
            centerText = "表情の割合"
            setCenterTextSize(16f)
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