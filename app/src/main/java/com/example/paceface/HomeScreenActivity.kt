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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class HomeScreenActivity : AppCompatActivity() {

    private lateinit var binding: HomeScreenBinding
    private lateinit var appDatabase: AppDatabase

    // Firebase + Room の連携で使うローカル DB の Int 型ユーザーID
    private lateinit var auth: FirebaseAuth
    private var localUserId: Int = -1

    // チャート更新ジョブ
    private var chartUpdateJob: Job? = null

    // 日付フォーマッタ
    private val dateFormatter = SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault())

    // SharedPreferences 用キー（表情設定）
    private val EMOJI_PREFS_NAME = "EmojiPrefs"
    private val KEY_SELECTED_EMOJI_TAG = "selectedEmojiTag"
    private val KEY_AUTO_CHANGE_ENABLED = "autoChangeEnabled"

    // 権限要求ランチャー
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val allGranted = permissions.entries.all { it.value == true }
            if (allGranted) {
                startLocationService()
            } else {
                Toast.makeText(this, "必要な権限が許可されていません。", Toast.LENGTH_LONG).show()
            }
        }

    // 速度更新を受け取るレシーバー
    private val speedUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null) return
            val speed = intent.getFloatExtra(LocationTrackingService.EXTRA_SPEED, 0f)
            updateSpeedUI(speed)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = HomeScreenBinding.inflate(layoutInflater)
        setContentView(binding.root)

        appDatabase = AppDatabase.getDatabase(this)
        auth = Firebase.auth

        // Firebase ユーザー検証と UI セットアップをライフサイクルスコープで実行
        lifecycleScope.launch {
            validateUserAndSetupScreen()
        }
    }

    private suspend fun validateUserAndSetupScreen() {
        val firebaseUser = auth.currentUser
        if (firebaseUser == null) {
            // 未ログインならログイン画面へ
            redirectToLogin()
            return
        }

        // Firebase UID からローカル DB のユーザーを取得
        val localUser = withContext(Dispatchers.IO) {
            appDatabase.userDao().getUserByFirebaseUid(firebaseUser.uid)
        }

        if (localUser == null) {
            Toast.makeText(this@HomeScreenActivity, "ユーザー情報の取得に失敗しました。", Toast.LENGTH_LONG).show()
            auth.signOut()
            redirectToLogin()
            return
        }

        localUserId = localUser.userId

        // UI の初期化
        setupUI()
    }

    private fun setupUI() {
        binding.tvTitle.text = "現在の歩行速度"
        setupNavigation()
        setupChart()
        loadTodayHistory()
        setupFooterNavigationIfExists()
    }

    private fun redirectToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    override fun onResume() {
        super.onResume()
        // 検証済みの localUserId がある場合のみ各種処理を開始する
        if (localUserId != -1) {
            checkPermissionsAndStartService()
            startChartUpdateLoop()
            LocalBroadcastManager.getInstance(this)
                .registerReceiver(speedUpdateReceiver, IntentFilter(LocationTrackingService.BROADCAST_SPEED_UPDATE))
        }

        // 表情設定の反映（表示系の更新）
        loadAndApplyEmotionSetting()
    }

    override fun onPause() {
        super.onPause()
        if (localUserId != -1) {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(speedUpdateReceiver)
            chartUpdateJob?.cancel()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // サービス停止 intent を送信
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

    private fun startLocationService() {
        val startIntent = Intent(this, LocationTrackingService::class.java).apply {
            action = LocationTrackingService.ACTION_START
            putExtra(LocationTrackingService.EXTRA_USER_ID, localUserId)
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
        updateFaceIconBasedOnSpeed(speed)
    }

    private fun updateFaceIconBasedOnSpeed(speed: Float) {
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

    private fun updateChartWithDataWindow() {
        lifecycleScope.launch {
            val now = Calendar.getInstance()
            val windowStart = (now.clone() as Calendar).apply { add(Calendar.MINUTE, -10) }.timeInMillis
            val windowEnd = (now.clone() as Calendar).apply { add(Calendar.MINUTE, 10) }.timeInMillis

            val historyInWindow = withContext(Dispatchers.IO) {
                appDatabase.historyDao().getHistoryForUserOnDate(localUserId, windowStart, windowEnd)
            }

            withContext(Dispatchers.Main) {
                binding.tvLastUpdate.text = "最終更新日時: ${dateFormatter.format(now.time)}"
                if (historyInWindow.isEmpty()) {
                    binding.lineChart.clear()
                    binding.lineChart.invalidate()
                } else {
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
    }

    private fun setupChart() {
        binding.lineChart.apply {
            description.isEnabled = false
            setNoDataText("今日の履歴はありません")
            isDragEnabled = true
            setScaleEnabled(true)
            setPinchZoom(true)
            legend.isEnabled = false
            xAxis.isEnabled = false
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
            val minSpeed = history.minOf { it.walkingSpeed }
            val maxSpeed = history.maxOf { it.walkingSpeed }
            val padding = (maxSpeed - minSpeed) * 0.2f + 0.5f
            yAxis.axisMinimum = (minSpeed - padding).coerceAtLeast(0f)
            yAxis.axisMaximum = maxSpeed + padding
        } else {
            yAxis.axisMinimum = 0f
            yAxis.axisMaximum = 5f
        }

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

    // プロジェクト側に footer のセットアップ関数があれば呼ぶ（元 master にあった名前に合わせる）
    private fun setupFooterNavigationIfExists() {
        try {
            setupNavigation()
        } catch (e: Exception) {
            // 無ければ無視
        }
    }

    private fun loadAndApplyEmotionSetting() {
        val emojiPrefs = getSharedPreferences(EMOJI_PREFS_NAME, Context.MODE_PRIVATE)
        val isAutoChangeEnabled = emojiPrefs.getBoolean(KEY_AUTO_CHANGE_ENABLED, false)

        if (isAutoChangeEnabled) {
            binding.tvStatus.text = "自動変更ON"
            // 自動変更は速度受信時に updateFaceIconBasedOnSpeed が呼ばれるため、ここでは特にしない
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

    private fun getDayBounds(timestamp: Long): Pair<Long, Long> {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = timestamp
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfDay = calendar.timeInMillis
        calendar.add(Calendar.DAY_OF_MONTH, 1)
        val endOfDay = calendar.timeInMillis
        return Pair(startOfDay, endOfDay)
    }

    private fun loadTodayHistory() {
        lifecycleScope.launch(Dispatchers.IO) {
            val (startOfDay, endOfDay) = getDayBounds(System.currentTimeMillis())
            val historyList = appDatabase.historyDao().getHistoryForUserOnDate(localUserId, startOfDay, endOfDay)

            withContext(Dispatchers.Main) {
                if (historyList.isEmpty()) {
                    binding.lineChart.clear()
                    binding.lineChart.invalidate()
                } else {
                    updateChart(historyList)
                }
            }
        }
    }

    // カスタムルールの自動生成チェック（最初のみ生成）
    private fun checkAndGenerateCustomRules() {
        val sharedPrefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        val areRulesGenerated = sharedPrefs.getBoolean("CUSTOM_RULES_GENERATED_$localUserId", false)

        if (areRulesGenerated) return

        lifecycleScope.launch(Dispatchers.IO) {
            generateAndSaveCustomRulesIfPossible()
        }
    }

    private suspend fun generateAndSaveCustomRulesIfPossible() {
        val speeds = appDatabase.historyDao().getAllWalkingSpeeds(localUserId).sorted()
        if (speeds.size < 10) return

        val p20 = speeds[(speeds.size * 0.20).toInt()]
        val p40 = speeds[(speeds.size * 0.40).toInt()]
        val p60 = speeds[(speeds.size * 0.60).toInt()]
        val p80 = speeds[minOf((speeds.size * 0.80).toInt(), speeds.lastIndex)]

        val newRules = listOf(
            SpeedRule(userId = localUserId, minSpeed = 0f, maxSpeed = p20, emotionId = 5),
            SpeedRule(userId = localUserId, minSpeed = p20, maxSpeed = p40, emotionId = 4),
            SpeedRule(userId = localUserId, minSpeed = p40, maxSpeed = p60, emotionId = 3),
            SpeedRule(userId = localUserId, minSpeed = p60, maxSpeed = p80, emotionId = 2),
            SpeedRule(userId = localUserId, minSpeed = p80, maxSpeed = Float.MAX_VALUE, emotionId = 1)
        )

        appDatabase.speedRuleDao().insertAll(newRules)

        withContext(Dispatchers.Main) {
            val sharedPrefsEditor = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE).edit()
            sharedPrefsEditor.putBoolean("CUSTOM_RULES_GENERATED_$localUserId", true)
            sharedPrefsEditor.apply()
        }
    }
}