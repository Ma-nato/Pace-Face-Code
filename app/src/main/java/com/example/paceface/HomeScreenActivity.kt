package com.example.paceface

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
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
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import androidx.core.graphics.toColorInt
import android.widget.ImageButton // ImageButtonをインポート
import android.view.animation.AlphaAnimation // 追加
import android.view.animation.Animation // 追加
import android.widget.TextView // 追加

class HomeScreenActivity : AppCompatActivity() {

    private lateinit var binding: HomeScreenBinding
    private lateinit var appDatabase: AppDatabase
    private var currentUserId: Int = -1
    private val dateFormatter = SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault())

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    // 1分間の速度データを保持するリスト
    private val speedReadings = mutableListOf<Float>()
    // 最終保存時刻
    private var lastSaveTimestamp = 0L

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                startLocationUpdates()
            } else {
                Toast.makeText(this, "位置情報の権限がありません。速度を計測できません。", Toast.LENGTH_LONG).show()
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
        lastSaveTimestamp = System.currentTimeMillis() // 初期化

        // --- ここから追加 ---
        // カードの初期位置をオフセットして、画面外からスライドインさせる
        binding.mainInfoCard.translationY = 200f
        binding.chartCard.translationY = 200f
        binding.mainInfoCard.alpha = 0f
        binding.chartCard.alpha = 0f

        binding.mainInfoCard.animate()
            .translationY(0f)
            .alpha(1f)
            .setDuration(500)
            .setStartDelay(200) // 少し遅れて開始
            .start()

        binding.chartCard.animate()
            .translationY(0f)
            .alpha(1f)
            .setDuration(500)
            .setStartDelay(400) // mainInfoCardよりさらに遅れて開始
            .start()
        // --- ここまで追加 ---
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
        // アプリ終了時に残っているデータを保存
        saveAverageSpeedToDb()
    }

    private fun checkLocationPermissionAndStartUpdates() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED -> {
                startLocationUpdates()
            }
            shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) -> {
                requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
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

                    // UIのリアルタイム更新
                    animateTextViewText(binding.tvSpeedValue, String.format(Locale.getDefault(), "%.1f km/h", speedKmh))
                    val statusText = "速度: " + if (speedKmh > 4.0) "速い" else "普通"
                    animateTextViewText(binding.tvStatus, statusText)
                    binding.tvLastUpdate.text = "最終更新日時: ${dateFormatter.format(Date())}"

                    // 顔アイコンの更新を追加
                    if (speedKmh > 4.0) {
                        // TODO: R.drawable.face_icon_fast を適切なリソースに置き換えてください
                        // binding.ivFaceIcon.setImageResource(R.drawable.face_icon_fast)
                    } else {
                        // TODO: R.drawable.face_icon_normal を適切なリソースに置き換えてください
                        // binding.ivFaceIcon.setImageResource(R.drawable.face_icon_normal)
                    }

                    if (speedKmh > 0) {
                        speedReadings.add(speedKmh)
                    }

                    // 1分経過したら平均速度をDBに保存
                    if (System.currentTimeMillis() - lastSaveTimestamp >= 60000) {
                        saveAverageSpeedToDb()
                    }
                }
            }
        }
    }

    private fun animateTextViewText(textView: TextView, newText: String) {
        val fadeOut = AlphaAnimation(1.0f, 0.0f)
        fadeOut.duration = 150
        fadeOut.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation?) {}
            override fun onAnimationEnd(animation: Animation?) {
                textView.text = newText
                val fadeIn = AlphaAnimation(0.0f, 1.0f)
                fadeIn.duration = 150
                textView.startAnimation(fadeIn)
            }
            override fun onAnimationRepeat(animation: Animation?) {}
        })
        textView.startAnimation(fadeOut)
    }

    private fun saveAverageSpeedToDb() {
        if (speedReadings.isEmpty()) {
            lastSaveTimestamp = System.currentTimeMillis()
            return
        }

        val averageSpeed = speedReadings.average().toFloat()
        lifecycleScope.launch {
            val newHistory = History(
                userId = currentUserId,
                timestamp = System.currentTimeMillis(),
                walkingSpeed = averageSpeed,
                acceleration = "", // Default value
                emotionId = 0 // Default value, assuming 0 means no emotion or unrecorded
            )
            appDatabase.historyDao().insert(newHistory)
            updateChartWithTodayData()
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
        // グラフデータの更新時にアニメーションを追加
        binding.lineChart.animateY(500) // Y軸方向に500ミリ秒でアニメーション
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
}