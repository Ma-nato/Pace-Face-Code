package com.example.paceface

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.paceface.databinding.HomeScreenBinding
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.material.R as R_material
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class HomeScreenActivity : AppCompatActivity() {

    private lateinit var binding: HomeScreenBinding
    private lateinit var appDatabase: AppDatabase
    private var currentUserId: Int = -1
    private val dateFormatter = SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault())

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    private val speedReadings = mutableListOf<Float>()
    private var lastSaveTimestamp = 0L

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            when {
                permissions.getOrDefault(Manifest.permission.ACCESS_BACKGROUND_LOCATION, false) -> {
                    startLocationUpdates()
                }
                permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false) -> {
                    Toast.makeText(this, "バックグラウンドでの位置情報アクセスが許可されなかったため、アプリがバックグラウンドにあると速度を記録できません。", Toast.LENGTH_LONG).show()
                    startLocationUpdates()
                }
                else -> {
                    Toast.makeText(this, "位置情報の権限がありません。速度を計測できません。", Toast.LENGTH_LONG).show()
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = HomeScreenBinding.inflate(layoutInflater)
        setContentView(binding.root)

        appDatabase = AppDatabase.getDatabase(this)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        val sharedPrefs = getSharedPreferences("AppPrefs", MODE_PRIVATE)
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
        createLocationCallback()
        lastSaveTimestamp = System.currentTimeMillis()

        checkAndGenerateCustomRules()

        binding.mainInfoCard.translationY = 200f
        binding.chartCard.translationY = 200f
        binding.mainInfoCard.alpha = 0f
        binding.chartCard.alpha = 0f

        binding.mainInfoCard.animate()
            .translationY(0f)
            .alpha(1f)
            .setDuration(500)
            .setStartDelay(200)
            .start()

        binding.chartCard.animate()
            .translationY(0f)
            .alpha(1f)
            .setDuration(500)
            .setStartDelay(400)
            .start()
    }

    override fun onResume() {
        super.onResume()
        checkLocationPermissionAndStartUpdates()
        updateChartWithTodayData()
    }

    override fun onPause() {
        super.onPause()
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    override fun onStop() {
        super.onStop()
        saveAverageSpeedToDb()
    }

    private fun checkLocationPermissionAndStartUpdates() {
        val hasFineLocationPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val hasBackgroundLocationPermission = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

            if (hasFineLocationPermission && hasBackgroundLocationPermission) {
                startLocationUpdates()
            } else {
                val permissionsToRequest = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)
                if (!hasBackgroundLocationPermission) {
                    permissionsToRequest.add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                }
                requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
            }
        } else {
            if (hasFineLocationPermission) {
                startLocationUpdates()
            } else {
                requestPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION))
            }
        }
    }

    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000)
            .setWaitForAccurateLocation(false)
            .setMinUpdateIntervalMillis(2000)
            .build()

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
        } catch (_: SecurityException) {
            Toast.makeText(this, "位置情報取得の権限がありません。", Toast.LENGTH_SHORT).show()
        }
    }

    private fun createLocationCallback() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    val speedKmh = location.speed * 3.6f

                    binding.tvSpeedValue.text = String.format(Locale.getDefault(), "%.1f km/h", speedKmh)
                    val statusText = "速度: " + if (speedKmh > 4.0) "速い" else "普通"
                    binding.tvStatus.text = statusText
                    binding.tvLastUpdate.text = "最終更新日時: ${dateFormatter.format(Date())}"

                    if (speedKmh > 4.0) {
                        // binding.ivFaceIcon.setImageResource(R.drawable.face_icon_fast)
                    } else {
                        // binding.ivFaceIcon.setImageResource(R.drawable.face_icon_normal)
                    }

                    if (speedKmh > 0) {
                        speedReadings.add(speedKmh)
                    }

                    if (System.currentTimeMillis() - lastSaveTimestamp >= 60000) {
                        saveAverageSpeedToDb()
                    }
                }
            }
        }
    }

    private fun saveAverageSpeedToDb() {
        if (speedReadings.isEmpty()) {
            lastSaveTimestamp = System.currentTimeMillis()
            return
        }

        val averageSpeed = speedReadings.average().toFloat()
        lifecycleScope.launch {
            try {
                val newHistory = History(
                    userId = currentUserId,
                    timestamp = System.currentTimeMillis(),
                    walkingSpeed = averageSpeed,
                    acceleration = "",
                    emotionId = 0
                )
                withContext(Dispatchers.IO) {
                    appDatabase.historyDao().insert(newHistory)
                }

                withContext(Dispatchers.Main) {
                    updateChartWithTodayData()
                    Toast.makeText(this@HomeScreenActivity, "速度データを保存しました。", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@HomeScreenActivity, "データの保存中にエラーが発生しました: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                    e.printStackTrace() // Log the exception for debugging
                }
            }
        }

        speedReadings.clear()
        lastSaveTimestamp = System.currentTimeMillis()
    }

    private fun updateChartWithTodayData() {
        lifecycleScope.launch {
            val today = Date()
            val cal = Calendar.getInstance().apply { time = today }
            val startOfDay = getStartOfDay(cal).timeInMillis
            val endOfDay = getEndOfDay(cal).timeInMillis

            val todayHistory = withContext(Dispatchers.IO) {
                appDatabase.historyDao().getHistoryForUserOnDate(currentUserId, startOfDay, endOfDay)
            }
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
        val entries = ArrayList<Entry>()

        // Group history by hour and calculate average speed for each hour
        val hourlyData = history.groupBy {
            val timeCal = Calendar.getInstance().apply { timeInMillis = it.timestamp }
            timeCal.get(Calendar.HOUR_OF_DAY)
        }.map { (hour, hourlyHistory) ->
            val averageSpeed = hourlyHistory.map { it.walkingSpeed }.average().toFloat()
            // Using the hour as the x-value for the chart
            Entry(hour.toFloat(), averageSpeed)
        }.sortedBy { it.x } // Sort by hour

        entries.addAll(hourlyData)

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

        // Only animate if there's new data.
        if (entries.isNotEmpty()) {
            binding.lineChart.animateY(500)
        }
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

        if (areRulesGenerated) {
            return // Already generated
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val firstTimestamp = appDatabase.historyDao().getFirstTimestamp(currentUserId)
            val lastTimestamp = appDatabase.historyDao().getLastTimestamp(currentUserId)

            if (firstTimestamp == null || lastTimestamp == null) {
                return@launch // Not enough data
            }

            val diffInMillis = lastTimestamp - firstTimestamp
            val diffInDays = TimeUnit.MILLISECONDS.toDays(diffInMillis)

            if (diffInDays >= 7) {
                generateAndSaveCustomRules()
            }
        }
    }

    private suspend fun generateAndSaveCustomRules() {
        val speeds = appDatabase.historyDao().getAllWalkingSpeeds(currentUserId).sorted()
        if (speeds.size < 10) return // Need a reasonable amount of data

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
