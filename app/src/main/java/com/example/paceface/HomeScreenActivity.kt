//HomeScreenActivity.kt
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
            redirectToLogin()
            return
        }

        // 基本的には LoginActivity で設定された SharedPreferences の ID を信頼して使う
        val sharedPrefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        val prefUserId = sharedPrefs.getInt("LOGGED_IN_USER_ID", -1)

        if (prefUserId != -1) {
            localUserId = prefUserId
        } else {
            // 万が一 Prefs が消えていた場合のフォールバック
            val localUser = withContext(Dispatchers.IO) {
                appDatabase.userDao().getUserByFirebaseUid(firebaseUser.uid)
            }
            if (localUser != null) {
                localUserId = localUser.userId
                // Prefsを復旧
                with(sharedPrefs.edit()) {
                    putInt("LOGGED_IN_USER_ID", localUserId)
                    apply()
                }
            } else {
                // ここに来るのは異常系だが、念のためログイン画面へ戻す
                redirectToLogin()
                return
            }
        }

        // UI の初期化
        setupUI()
    }

    private fun setupUI() {
        binding.tvTitle.text = "現在の歩行速度"
        setupNavigation()
        setupChart()
        loadTodayHistory()
        setupFooterNavigationIfExists()

        // ここに移動
        checkPermissionsAndStartService()
        startChartUpdateLoop()
        LocalBroadcastManager.getInstance(this)
            .registerReceiver(speedUpdateReceiver, IntentFilter(LocationTrackingService.BROADCAST_SPEED_UPDATE))

        // 表情設定の反映（表示系の更新）
        loadAndApplyEmotionSetting()
    }

    override fun onResume() {
        super.onResume()
        // onResume から localUserId != -1 のチェックと関連処理を削除する (setupUI() に移動したため)
        // ただし、表情設定の反映はonResumeの度に必要なので残す
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
        // 自動変更設定を確認する処理
        val emojiPrefs = getSharedPreferences(EMOJI_PREFS_NAME, Context.MODE_PRIVATE)
        val isAutoChangeEnabled = emojiPrefs.getBoolean(KEY_AUTO_CHANGE_ENABLED, false)

        // 自動変更がOFFの場合、速度によるアイコン更新を行わずに終了する
        if (!isAutoChangeEnabled) {
            return
        }

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
            setNoDataText("データ待機中...")

            // タッチ操作の設定
            isDragEnabled = true
            setScaleEnabled(true)
            setPinchZoom(true)

            // 凡例は隠す（1つのデータしかないので不要）
            legend.isEnabled = false

            // --- X軸（時間）の設定 ---
            xAxis.apply {
                isEnabled = true
                position = com.github.mikephil.charting.components.XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false) // X軸のグリッドはうるさくなるのでOFF
                textColor = Color.DKGRAY
                textSize = 10f
                granularity = 1f // 1分ごとにデータを区切る

                // 数字（分）を「HH:mm」形式に変換するフォーマッターを設定
                valueFormatter = object : com.github.mikephil.charting.formatter.ValueFormatter() {
                    override fun getAxisLabel(value: Float, axis: com.github.mikephil.charting.components.AxisBase?): String {
                        val totalMinutes = value.toInt()
                        val hour = totalMinutes / 60
                        val minute = totalMinutes % 60
                        // 24時間を超える場合の補正（念のため）
                        val normalizedHour = hour % 24
                        return String.format(Locale.getDefault(), "%02d:%02d", normalizedHour, minute)
                    }
                }
            }

            // --- Y軸（速度）の設定 ---
            axisRight.isEnabled = false // 右側の軸は消す
            axisLeft.apply {
                isEnabled = true
                textColor = Color.DKGRAY
                setDrawGridLines(true) // 横のグリッド線を表示
                gridColor = Color.LTGRAY
                enableGridDashedLine(10f, 10f, 0f) // グリッドを点線にする
                axisMinimum = 0f // 常に0からスタートさせる
            }

            // 余白の調整
            setExtraOffsets(10f, 10f, 10f, 10f)
        }
    }

    private fun updateChart(history: List<History>) {
        val entries = history.map {
            val timeCal = Calendar.getInstance().apply { timeInMillis = it.timestamp }
            val minuteOfDay = timeCal.get(Calendar.HOUR_OF_DAY) * 60 + timeCal.get(Calendar.MINUTE)
            Entry(minuteOfDay.toFloat(), it.walkingSpeed)
        }.sortedBy { it.x }

        // Y軸の自動調整（最大値に少し余裕を持たせる）
        val yAxis = binding.lineChart.axisLeft
        if (history.isNotEmpty()) {
            val maxSpeed = history.maxOf { it.walkingSpeed }
            // 最大値の1.2倍くらいを上限にして、グラフが天井に張り付かないようにする
            yAxis.axisMaximum = (maxSpeed * 1.2f).coerceAtLeast(5f)
        } else {
            yAxis.axisMaximum = 5f
        }

        val primaryColor = ContextCompat.getColor(this, R_material.color.design_default_color_primary)

        val dataSet = LineDataSet(ArrayList(entries), "歩行速度").apply {
            // --- 線のデザイン ---
            color = primaryColor
            lineWidth = 3f // 線を少し太く
            mode = LineDataSet.Mode.CUBIC_BEZIER // ★カクカクではなく滑らかな曲線にする

            // --- 点のデザイン ---
            setDrawCircles(true)
            setCircleColor(primaryColor)
            circleRadius = 3f
            setDrawCircleHole(false)

            // --- 塗りつぶしのデザイン ---
            setDrawFilled(true)
            fillColor = primaryColor
            fillAlpha = 50 // 透明度（0-255）

            // --- 値のテキスト表示 ---
            setDrawValues(false) // ★グラフ上の数字をごちゃごちゃさせないためにOFFにする（タップで確認させる想定）
            // もし数字を出したい場合は true にして以下を設定
            // valueTextColor = Color.BLACK
            // valueTextSize = 10f
        }

        val lineData = LineData(dataSet)
        binding.lineChart.data = lineData

        // アニメーションを入れると更新感が出ます（お好みで）
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