package com.example.paceface

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.paceface.databinding.HomeScreenBinding
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.google.android.material.R as R_material
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class HomeScreenActivity : AppCompatActivity() {

    private lateinit var binding: HomeScreenBinding
    private lateinit var appDatabase: AppDatabase
    private var currentUserId: Int = -1
    private val dateFormatter = SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault())
    private var chartUpdateJob: Job? = null

    private val speedUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == LocationTrackingService.BROADCAST_SPEED_UPDATE) {
                val speed = intent.getFloatExtra(LocationTrackingService.EXTRA_SPEED, 0f)
                updateSpeedUI(speed)
            }
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        handlePermissionsResult(permissions)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = HomeScreenBinding.inflate(layoutInflater)
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

        binding.tvTitle.text = "現在の歩行速度"
        setupNavigation()
        setupChart()
        checkAndGenerateCustomRules()
    }

    override fun onResume() {
        super.onResume()
        checkPermissionsAndStartService()
        startChartUpdateLoop()
        LocalBroadcastManager.getInstance(this).registerReceiver(speedUpdateReceiver, IntentFilter(LocationTrackingService.BROADCAST_SPEED_UPDATE))
    }

    override fun onPause() {
        super.onPause()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(speedUpdateReceiver)
        chartUpdateJob?.cancel()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Stop the service if the app is destroyed
        val stopIntent = Intent(this, LocationTrackingService::class.java).apply {
            action = LocationTrackingService.ACTION_STOP
        }
        startService(stopIntent)
    }

    private fun checkPermissionsAndStartService() {
        val requiredPermissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            requiredPermissions.add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requiredPermissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val permissionsToRequest = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (permissionsToRequest.isEmpty()) {
            startLocationService()
        } else {
            requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    private fun handlePermissionsResult(permissions: Map<String, Boolean>) {
        val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true

        if (!fineLocationGranted) {
            Toast.makeText(this, "位置情報の権限がありません。", Toast.LENGTH_LONG).show()
            return
        }

        // On Android 10 (Q) and higher, check for background permission separately.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                startLocationService()
            } else {
                // This Toast is important to guide the user.
                Toast.makeText(this, "バックグラウンドでの位置情報アクセスを「常に許可」に設定してください。", Toast.LENGTH_LONG).show()
                // Optionally, you could guide them to settings here.
            }
        } else {
            // For older versions, fine location is enough.
            startLocationService()
        }
    }

    private fun startLocationService() {
        val startIntent = Intent(this, LocationTrackingService::class.java).apply {
            action = LocationTrackingService.ACTION_START
            putExtra(LocationTrackingService.EXTRA_USER_ID, currentUserId)
        }
        startService(startIntent)
    }

    private fun startChartUpdateLoop() {
        chartUpdateJob?.cancel()
        chartUpdateJob = lifecycleScope.launch {
            while (isActive) {
                updateChartWithDataWindow()
                val now = Calendar.getInstance()
                val seconds = now.get(Calendar.SECOND)
                val delayMillis = (60 - seconds) * 1000L
                delay(delayMillis)
            }
        }
    }

    private fun updateSpeedUI(speed: Float) {
        binding.tvSpeedValue.text = String.format(Locale.getDefault(), "%.1f", speed)
        updateFaceIcon(speed)
    }

    private fun updateFaceIcon(speed: Float) {
        lifecycleScope.launch {
            val speedRule = withContext(Dispatchers.IO) {
                appDatabase.speedRuleDao().getSpeedRuleForSpeed(currentUserId, speed)
            }
            val faceIconResId = when (speedRule?.emotionId) {
                1 -> R.drawable.impatient_expression
                2 -> R.drawable.smile_expression
                3 -> R.drawable.smile_expression
                4 -> R.drawable.normal_expression
                5 -> R.drawable.sad_expression
                else -> R.drawable.normal_expression
            }
            binding.ivFaceIcon.setImageResource(faceIconResId)
        }
    }

    private fun updateChartWithDataWindow() {
        lifecycleScope.launch {
            val now = Calendar.getInstance()
            val windowStart = (now.clone() as Calendar).apply { add(Calendar.MINUTE, -10) }.timeInMillis
            val windowEnd = (now.clone() as Calendar).apply { add(Calendar.MINUTE, 10) }.timeInMillis

            val historyInWindow = withContext(Dispatchers.IO) {
                appDatabase.historyDao().getHistoryForUserOnDate(currentUserId, windowStart, windowEnd)
            }

            withContext(Dispatchers.Main) {
                binding.tvLastUpdate.text = "最終更新日時: ${dateFormatter.format(now.time)}"
                updateChart(historyInWindow)

                val currentMinuteOfDay = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
                val minX = (currentMinuteOfDay - 10).toFloat()
                val maxX = (currentMinuteOfDay + 10).toFloat()
                binding.lineChart.xAxis.axisMinimum = minX
                binding.lineChart.xAxis.axisMaximum = maxX
                binding.lineChart.invalidate()
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

            xAxis.valueFormatter = object : ValueFormatter() {
                private val format = SimpleDateFormat("HH:mm", Locale.getDefault())
                override fun getFormattedValue(value: Float): String {
                    val totalMinutes = value.toInt()
                    val hours = (totalMinutes / 60) % 24
                    val minutes = totalMinutes % 60
                    val calendar = Calendar.getInstance().apply {
                        set(Calendar.HOUR_OF_DAY, hours)
                        set(Calendar.MINUTE, minutes)
                    }
                    return format.format(calendar.time)
                }
            }

            val leftAxis = binding.lineChart.axisLeft
            leftAxis.valueFormatter = object : ValueFormatter() {
                private val format = DecimalFormat("0.0", DecimalFormatSymbols(Locale.getDefault()))
                override fun getFormattedValue(value: Float): String {
                    return "${format.format(value)} km/h"
                }
            }
            binding.lineChart.axisRight.isEnabled = false
        }
    }

    private fun updateChart(history: List<History>) {
        val entries = history.map {
            val timeCal = Calendar.getInstance().apply { timeInMillis = it.timestamp }
            val minuteOfDay = timeCal.get(Calendar.HOUR_OF_DAY) * 60 + timeCal.get(Calendar.MINUTE)
            Entry(minuteOfDay.toFloat(), it.walkingSpeed)
        }.sortedBy { it.x }

        val dataSet = LineDataSet(ArrayList(entries), "歩行速度").apply {
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

    private fun setupNavigation() {
        NavigationUtils.setupCommonNavigation(
            this,
            HomeScreenActivity::class.java,
            binding.homeButton,
            binding.passingButton,
            binding.historyButton,
            binding.emotionButton,
            binding.gearButton
        )
    }

    private fun checkAndGenerateCustomRules() {
        val sharedPrefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        val areRulesGenerated = sharedPrefs.getBoolean("CUSTOM_RULES_GENERATED_$currentUserId", false)

        if (areRulesGenerated) return

        lifecycleScope.launch(Dispatchers.IO) {
            val firstTimestamp = appDatabase.historyDao().getFirstTimestamp(currentUserId)
            val lastTimestamp = appDatabase.historyDao().getLastTimestamp(currentUserId)

            if (firstTimestamp == null || lastTimestamp == null) return@launch

            val diffInMillis = lastTimestamp - firstTimestamp
            val diffInDays = TimeUnit.MILLISECONDS.toDays(diffInMillis)

            if (diffInDays >= 7) {
                generateAndSaveCustomRules()
            }
        }
    }

    private suspend fun generateAndSaveCustomRules() {
        val speeds = appDatabase.historyDao().getAllWalkingSpeeds(currentUserId).sorted()
        if (speeds.size < 10) return

        val p20 = speeds[(speeds.size * 0.20).toInt()]
        val p40 = speeds[(speeds.size * 0.40).toInt()]
        val p60 = speeds[(speeds.size * 0.60).toInt()]
        val p80 = speeds[minOf((speeds.size * 0.80).toInt(), speeds.lastIndex)]

        val newRules = listOf(
            SpeedRule(userId = currentUserId, minSpeed = 0f, maxSpeed = p20, emotionId = 5),      // Sad
            SpeedRule(userId = currentUserId, minSpeed = p20, maxSpeed = p40, emotionId = 4),    // Neutral
            SpeedRule(userId = currentUserId, minSpeed = p40, maxSpeed = p60, emotionId = 3),    // Happy
            SpeedRule(userId = currentUserId, minSpeed = p60, maxSpeed = p80, emotionId = 2),    // Excited
            SpeedRule(userId = currentUserId, minSpeed = p80, maxSpeed = Float.MAX_VALUE, emotionId = 1) // Surprise
        )

        appDatabase.speedRuleDao().insertAll(newRules)

        withContext(Dispatchers.Main) {
            val sharedPrefsEditor = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE).edit()
            sharedPrefsEditor.putBoolean("CUSTOM_RULES_GENERATED_$currentUserId", true)
            sharedPrefsEditor.apply()
        }
    }
}
