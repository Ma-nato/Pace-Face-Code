package com.example.paceface

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import com.google.android.material.R as R_material
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class HomeScreenActivity : AppCompatActivity() {

    private lateinit var binding: HomeScreenBinding
    private lateinit var appDatabase: AppDatabase
    private lateinit var viewModel: HomeViewModel

    private lateinit var auth: FirebaseAuth
    private var localUserId: Int = -1

    private val dateFormatter = SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault())

    private val EMOJI_PREFS_NAME = "EmojiPrefs"
    private val KEY_SELECTED_EMOJI_TAG = "selectedEmojiTag"
    private val KEY_AUTO_CHANGE_ENABLED = "autoChangeEnabled"

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val allGranted = permissions.entries.all { it.value == true }
            if (allGranted) {
                startLocationService()
            } else {
                Toast.makeText(this, "必要な権限が許可されていません。", Toast.LENGTH_LONG).show()
            }
        }

    private val speedUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null) return
            val speed = intent.getFloatExtra(LocationTrackingService.EXTRA_SPEED, 0f)
            viewModel.updateSpeed(speed)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = HomeScreenBinding.inflate(layoutInflater)
        setContentView(binding.root)

        appDatabase = AppDatabase.getDatabase(this)
        auth = Firebase.auth

        lifecycleScope.launch {
            validateUserAndSetupScreen()
        }
    }

    private fun setupViewModel() {
        viewModel = HomeViewModel(appDatabase.historyDao(), localUserId)

        lifecycleScope.launch {
            viewModel.currentSpeed.collect { speed ->
                updateSpeedUI(speed)
            }
        }

        lifecycleScope.launch {
            viewModel.historyData.collect { history ->
                if (history.isEmpty()) {
                    binding.lineChart.clear()
                    binding.lineChart.invalidate()
                } else {
                    updateChart(history)
                    val now = Calendar.getInstance()
                    val currentMinuteOfDay = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
                    binding.lineChart.xAxis.axisMinimum = (currentMinuteOfDay - 10).toFloat()
                    binding.lineChart.xAxis.axisMaximum = (currentMinuteOfDay + 10).toFloat()
                    binding.lineChart.invalidate()
                }
            }
        }
    }

    private suspend fun validateUserAndSetupScreen() {
        val firebaseUser = auth.currentUser
        if (firebaseUser == null) {
            redirectToLogin()
            return
        }

        val sharedPrefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        val prefUserId = sharedPrefs.getInt("LOGGED_IN_USER_ID", -1)

        if (prefUserId != -1) {
            localUserId = prefUserId
        } else {
            val localUser = withContext(Dispatchers.IO) {
                appDatabase.userDao().getUserByFirebaseUid(firebaseUser.uid)
            }
            if (localUser != null) {
                localUserId = localUser.userId
                with(sharedPrefs.edit()) {
                    putInt("LOGGED_IN_USER_ID", localUserId)
                    apply()
                }
            } else {
                redirectToLogin()
                return
            }
        }

        setupUI()
    }

    private fun setupUI() {
        binding.tvTitle.text = "現在の歩行速度"
        setupNavigation()
        setupChart()
        setupViewModel()

        checkPermissionsAndStartService()
        LocalBroadcastManager.getInstance(this)
            .registerReceiver(speedUpdateReceiver, IntentFilter(LocationTrackingService.BROADCAST_SPEED_UPDATE))

        loadAndApplyEmotionSetting()
    }

    override fun onResume() {
        super.onResume()
        loadAndApplyEmotionSetting()
    }

    private fun redirectToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    override fun onPause() {
        super.onPause()
        if (localUserId != -1) {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(speedUpdateReceiver)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        val stopIntent = Intent(this, LocationTrackingService::class.java).apply {
            action = LocationTrackingService.ACTION_STOP
        }
        startService(stopIntent)
    }

    private fun checkPermissionsAndStartService() {
        val requiredPermissions = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)
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

    private fun startLocationService() {
        val startIntent = Intent(this, LocationTrackingService::class.java).apply {
            action = LocationTrackingService.ACTION_START
            putExtra(LocationTrackingService.EXTRA_USER_ID, localUserId)
        }
        startService(startIntent)
    }

    private fun updateSpeedUI(speed: Float) {
        binding.tvSpeedValue.text = String.format(Locale.getDefault(), "%.1f", speed)
        updateFaceIconBasedOnSpeed(speed)
    }

    private fun updateFaceIconBasedOnSpeed(speed: Float) {
        val emojiPrefs = getSharedPreferences(EMOJI_PREFS_NAME, Context.MODE_PRIVATE)
        val isAutoChangeEnabled = emojiPrefs.getBoolean(KEY_AUTO_CHANGE_ENABLED, false)

        if (!isAutoChangeEnabled) return

        lifecycleScope.launch {
            val speedRule = withContext(Dispatchers.IO) {
                appDatabase.speedRuleDao().getSpeedRuleForSpeed(localUserId, speed)
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

    private fun setupChart() {
        binding.lineChart.apply {
            description.isEnabled = false
            setNoDataText("データ待機中...")
            isDragEnabled = true
            setScaleEnabled(true)
            setPinchZoom(true)
            legend.isEnabled = false

            xAxis.apply {
                isEnabled = true
                position = com.github.mikephil.charting.components.XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                textColor = Color.DKGRAY
                textSize = 10f
                granularity = 1f
                valueFormatter = object : com.github.mikephil.charting.formatter.ValueFormatter() {
                    override fun getAxisLabel(value: Float, axis: com.github.mikephil.charting.components.AxisBase?): String {
                        val totalMinutes = value.toInt()
                        val hour = (totalMinutes / 60) % 24
                        val minute = totalMinutes % 60
                        return String.format(Locale.getDefault(), "%02d:%02d", hour, minute)
                    }
                }
            }

            axisRight.isEnabled = false
            axisLeft.apply {
                isEnabled = true
                textColor = Color.DKGRAY
                setDrawGridLines(true)
                gridColor = Color.LTGRAY
                enableGridDashedLine(10f, 10f, 0f)
                axisMinimum = 0f
            }
            setExtraOffsets(10f, 10f, 10f, 10f)
        }
    }

    private fun updateChart(history: List<History>) {
        val entries = history.map {
            val timeCal = Calendar.getInstance().apply { timeInMillis = it.timestamp }
            val minuteOfDay = timeCal.get(Calendar.HOUR_OF_DAY) * 60 + timeCal.get(Calendar.MINUTE)
            Entry(minuteOfDay.toFloat(), it.walkingSpeed)
        }.sortedBy { it.x }

        val yAxis = binding.lineChart.axisLeft
        if (history.isNotEmpty()) {
            val maxSpeed = history.maxOf { it.walkingSpeed }
            yAxis.axisMaximum = (maxSpeed * 1.2f).coerceAtLeast(5f)
        } else {
            yAxis.axisMaximum = 5f
        }

        val primaryColor = ContextCompat.getColor(this, R_material.color.design_default_color_primary)

        val dataSet = LineDataSet(ArrayList(entries), "歩行速度").apply {
            color = primaryColor
            lineWidth = 3f
            mode = LineDataSet.Mode.CUBIC_BEZIER
            setDrawCircles(true)
            setCircleColor(primaryColor)
            circleRadius = 3f
            setDrawCircleHole(false)
            setDrawFilled(true)
            fillColor = primaryColor
            fillAlpha = 50
            setDrawValues(false)
        }

        binding.lineChart.data = LineData(dataSet)
        binding.lineChart.animateY(500)
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

    private fun loadAndApplyEmotionSetting() {
        val emojiPrefs = getSharedPreferences(EMOJI_PREFS_NAME, Context.MODE_PRIVATE)
        val isAutoChangeEnabled = emojiPrefs.getBoolean(KEY_AUTO_CHANGE_ENABLED, false)

        if (isAutoChangeEnabled) {
            binding.tvStatus.text = "自動変更ON"
        } else {
            val savedTag = emojiPrefs.getString(KEY_SELECTED_EMOJI_TAG, "1") ?: "1"
            applyManualFaceIcon(savedTag)
            binding.tvStatus.text = "表情固定中"
        }
    }

    private fun applyManualFaceIcon(emotionIdTag: String) {
        val drawableId = when (emotionIdTag) {
            "1" -> R.drawable.normal_expression
            "2" -> R.drawable.troubled_expression
            "3" -> R.drawable.impatient_expression
            "4" -> R.drawable.smile_expression
            "5" -> R.drawable.sad_expression
            "6" -> R.drawable.angry_expression
            else -> R.drawable.normal_expression
        }
        binding.ivFaceIcon.setImageResource(drawableId)
    }
}
