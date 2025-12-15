//HomeScreenActivity.kt
package com.example.paceface

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.paceface.databinding.HomeScreenBinding
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.google.android.material.R as R_material
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

class HomeScreenActivity : AppCompatActivity() {

    private lateinit var binding: HomeScreenBinding
    private lateinit var appDatabase: AppDatabase
    private lateinit var auth: FirebaseAuth
    private var localUserId: Int = -1 // ローカルDB用のInt型ID

    private val dateFormatter = SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault())
    private var chartUpdateJob: Job? = null

    // SharedPreferencesから表情設定を読み込むためのキー
    private val EMOJI_PREFS_NAME = "EmojiPrefs"
    private val KEY_SELECTED_EMOJI_TAG = "selectedEmojiTag"
    private val KEY_AUTO_CHANGE_ENABLED = "autoChangeEnabled"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = HomeScreenBinding.inflate(layoutInflater)
        setContentView(binding.root) // ★★★ 最初にレイアウトをセット ★★★

        appDatabase = AppDatabase.getDatabase(this)
        auth = Firebase.auth

        // ユーザー検証とUIセットアップをライフサイクルスコープで開始
        lifecycleScope.launch {
            validateUserAndSetupScreen()
        }
    }

    private suspend fun validateUserAndSetupScreen() {
        val firebaseUser = auth.currentUser
        if (firebaseUser == null) {
            // ログインしていない場合はログイン画面へ
            redirectToLogin()
            return
        }

        // Firebase UIDからローカルのユーザー情報を取得
        val localUser = withContext(Dispatchers.IO) {
            appDatabase.userDao().getUserByFirebaseUid(firebaseUser.uid)
        }

        if (localUser == null) {
            // ローカルDBにユーザー情報が見つからない（異常事態）
            Toast.makeText(this@HomeScreenActivity, "ユーザー情報の取得に失敗しました。", Toast.LENGTH_LONG).show()
            auth.signOut() // Firebaseからサインアウト
            redirectToLogin()
            return
        }

        // ローカルのInt型IDをセット
        localUserId = localUser.userId

        // ★★★ 検証成功後にUIセットアップを呼び出す ★★★
        setupUI()
    }

    private fun setupUI() {
        binding.tvTitle.text = "現在の歩行速度"
        setupNavigation()
        setupChart()
        loadTodayHistory()
        setupFooterNavigation()
    }

    private fun redirectToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }


    override fun onResume() {
        super.onResume()
        // ユーザーIDが有効な場合のみ（検証成功後）サービスを開始
        if (localUserId != -1) {
            checkPermissionsAndStartService()
            startChartUpdateLoop()
            LocalBroadcastManager.getInstance(this).registerReceiver(speedUpdateReceiver, IntentFilter(LocationTrackingService.BROADCAST_SPEED_UPDATE))
        }
    }

    override fun onPause() {
        super.onPause()
        // ユーザーIDが有効な場合のみレシーバーを解除
        if (localUserId != -1) {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(speedUpdateReceiver)
            chartUpdateJob?.cancel()
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

    private fun loadAndApplyEmotionSetting() {
        val emojiPrefs = getSharedPreferences(EMOJI_PREFS_NAME, Context.MODE_PRIVATE)
        val isAutoChangeEnabled = emojiPrefs.getBoolean(KEY_AUTO_CHANGE_ENABLED, false)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                startLocationService()
            } else {
                Toast.makeText(this, "バックグラウンドでの位置情報アクセスを「常に許可」に設定してください。", Toast.LENGTH_LONG).show()
            }
        } else {
            startLocationService()
        }
    }

    private fun startLocationService() {
        val startIntent = Intent(this, LocationTrackingService::class.java).apply {
            action = LocationTrackingService.ACTION_START
            putExtra(LocationTrackingService.EXTRA_USER_ID, localUserId) // Int型のIDを渡す
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
                updateChart(historyInWindow)

                val currentMinuteOfDay = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
                val minX = (currentMinuteOfDay - 10).toFloat()
                val maxX = (currentMinuteOfDay + 10).toFloat()
                binding.lineChart.xAxis.axisMinimum = minX
                binding.lineChart.xAxis.axisMaximum = maxX
                binding.lineChart.invalidate()
            }
        }
        binding.ivFaceIcon.setImageResource(drawableId)
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

    private fun checkAndGenerateCustomRules() {
        val sharedPrefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        val areRulesGenerated = sharedPrefs.getBoolean("CUSTOM_RULES_GENERATED_$localUserId", false)

        if (areRulesGenerated) return
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
            val firstTimestamp = appDatabase.historyDao().getFirstTimestamp(localUserId)
            val lastTimestamp = appDatabase.historyDao().getLastTimestamp(localUserId)

            if (firstTimestamp == null || lastTimestamp == null) return@launch

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

    private suspend fun generateAndSaveCustomRules() {
        val speeds = appDatabase.historyDao().getAllWalkingSpeeds(localUserId).sorted()
        if (speeds.size < 10) return

        val p20 = speeds[(speeds.size * 0.20).toInt()]
        val p40 = speeds[(speeds.size * 0.40).toInt()]
        val p60 = speeds[(speeds.size * 0.60).toInt()]
        val p80 = speeds[minOf((speeds.size * 0.80).toInt(), speeds.lastIndex)]

        val newRules = listOf(
            SpeedRule(userId = localUserId, minSpeed = 0f, maxSpeed = p20, emotionId = 5),      // Sad
            SpeedRule(userId = localUserId, minSpeed = p20, maxSpeed = p40, emotionId = 4),    // Neutral
            SpeedRule(userId = localUserId, minSpeed = p40, maxSpeed = p60, emotionId = 3),    // Happy
            SpeedRule(userId = localUserId, minSpeed = p60, maxSpeed = p80, emotionId = 2),    // Excited
            SpeedRule(userId = localUserId, minSpeed = p80, maxSpeed = Float.MAX_VALUE, emotionId = 1) // Surprise
        )

        appDatabase.speedRuleDao().insertAll(newRules)

        withContext(Dispatchers.Main) {
            val sharedPrefsEditor = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE).edit()
            sharedPrefsEditor.putBoolean("CUSTOM_RULES_GENERATED_$localUserId", true)
            sharedPrefsEditor.apply()
    private fun updateChart(historyList: List<History>) {
        val entries = ArrayList<Entry>()
        historyList.forEach {
            val timeOffset = (it.timestamp % (24 * 60 * 60 * 1000)).toFloat()
            entries.add(Entry(timeOffset, it.emotionId.toFloat()))
        }

        if (entries.isNotEmpty()) {
            val dataSet = LineDataSet(entries, "今日の表情の変化").apply {
                color = Color.MAGENTA
                valueTextColor = Color.BLACK
                setCircleColor(Color.MAGENTA)
                circleRadius = 4f
                lineWidth = 2f
            }
            val lineData = LineData(dataSet)
            binding.lineChart.data = lineData
            binding.lineChart.invalidate()
        }
    }
}