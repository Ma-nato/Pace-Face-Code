package com.example.paceface

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.paceface.databinding.HomeScreenBinding
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.google.android.material.R as R_material
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.sqrt

class HomeScreenActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var binding: HomeScreenBinding
    private lateinit var appDatabase: AppDatabase
    private lateinit var sensorManager: SensorManager
    private var linearAccelSensor: Sensor? = null
    private var currentUserId: Int = -1
    private val dateFormatter = SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault())
    
    private val velocity = FloatArray(3) { 0f }
    private var lastTimestamp: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = HomeScreenBinding.inflate(layoutInflater)
        setContentView(binding.root)

        appDatabase = AppDatabase.getDatabase(this)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        linearAccelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)

        val sharedPrefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        currentUserId = sharedPrefs.getInt("LOGGED_IN_USER_ID", -1)

        if (currentUserId == -1) {
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
            return
        }
        
        binding.tvTitle.text = "今日の歩行速度"

        setupNavigation()
        setupChart()
    }

    override fun onResume() {
        super.onResume()
        linearAccelSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
        updateChartWithTodayData()
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) { }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_LINEAR_ACCELERATION) {
            if (lastTimestamp == 0L) {
                lastTimestamp = event.timestamp
                return
            }

            val dt = (event.timestamp - lastTimestamp) / 1_000_000_000.0f
            lastTimestamp = event.timestamp

            val ax = event.values[0]
            val ay = event.values[1]
            val az = event.values[2]

            velocity[0] += ax * dt
            velocity[1] += ay * dt
            velocity[2] += az * dt

            val accelerationMagnitude = sqrt(ax * ax + ay * ay + az * az)
            if (accelerationMagnitude < 0.2f) {
                velocity[0] *= 0.9f
                velocity[1] *= 0.9f
                velocity[2] *= 0.9f
            }

            val currentSpeedMs = sqrt(velocity[0] * velocity[0] + velocity[1] * velocity[1] + velocity[2] * velocity[2])
            val speedKmh = currentSpeedMs * 3.6

            binding.tvSpeedValue.text = String.format("%.1f km/h", speedKmh)
            binding.tvStatus.text = "速度: " + if (speedKmh > 4.0) "速い" else "普通"
            binding.tvLastUpdate.text = "最終更新日時: ${dateFormatter.format(Date())}"
        }
    }

    private fun updateChartWithTodayData() {
        lifecycleScope.launch {
            val today = Date()
            val cal = Calendar.getInstance().apply { time = today }
            val startOfDay = getStartOfDay(cal).timeInMillis
            val endOfDay = getEndOfDay(cal).timeInMillis

            val todayHistory = appDatabase.historyDao().getHistoryForUserOnDate(currentUserId, startOfDay, endOfDay)
            updateChart(todayHistory)
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
        }
    }

    private fun updateChart(history: List<History>) {
        if (history.isEmpty()) {
            binding.lineChart.clear()
            binding.lineChart.invalidate()
            return
        }

        val entries = ArrayList<Entry>()
        history.forEach {
            val timeCal = Calendar.getInstance().apply { timeInMillis = it.timestamp }
            val hour = timeCal.get(Calendar.HOUR_OF_DAY).toFloat()
            val minute = timeCal.get(Calendar.MINUTE).toFloat() / 60f
            entries.add(Entry(hour + minute, it.walkingSpeed))
        }

        val dataSet = LineDataSet(entries, "歩行速度").apply {
            color = ContextCompat.getColor(this@HomeScreenActivity, R_material.color.design_default_color_primary)
            valueTextColor = Color.BLACK
            setCircleColor(color)
            circleRadius = 4f
            lineWidth = 2f
        }

        val lineData = LineData(dataSet)
        binding.lineChart.data = lineData
        binding.lineChart.invalidate()
    }

    private fun getStartOfDay(calendar: Calendar): Calendar {
        val cal = calendar.clone() as Calendar
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal
    }

    private fun getEndOfDay(calendar: Calendar): Calendar {
        val cal = calendar.clone() as Calendar
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.set(Calendar.MILLISECOND, 999)
        return cal
    }

    private fun setupNavigation() {
        binding.homeButton.setBackgroundColor(Color.parseColor("#33000000"))
        binding.passingButton.setOnClickListener { navigateTo(ProximityHistoryScreenActivity::class.java) }
        binding.historyButton.setOnClickListener { navigateTo(HistoryScreenActivity::class.java) }
        binding.emotionButton.setOnClickListener { /* TODO */ }
        binding.gearButton.setOnClickListener { navigateTo(UserSettingsScreenActivity::class.java) }
    }

    private fun <T : AppCompatActivity> navigateTo(activityClass: Class<T>) {
        val intent = Intent(this, activityClass)
        startActivity(intent)
        overridePendingTransition(0, 0)
    }
}
