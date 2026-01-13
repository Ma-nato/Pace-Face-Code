//HomeScreenActivity.kt
package com.example.paceface

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
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
import java.io.IOException
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.UUID

class HomeScreenActivity : AppCompatActivity() {

    private val SPP_UUID: UUID =
        UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    private var isConnecting = false

    private var piOutputStream: OutputStream? = null

    private var piSocket: BluetoothSocket? = null

    private var bluetoothSocket: BluetoothSocket? = null

    private var lastSentEmotionId: Int? = null

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
            Log.d("PaceFace", "① Broadcast received")
            if (intent == null) {
                Log.e("PaceFace", "intent is null")
                return
            }
            val speed = intent.getFloatExtra(
                LocationTrackingService.EXTRA_SPEED,
                -1f
            )
            Log.d("PaceFace", "② Broadcast speed=$speed")
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
        checkAndInsertDefaultSpeedRules() // SpeedRuleの初期値設定を追加
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
        //仮追加
        checkAndInsertDefaultSpeedRules()

        connectToRaspberryPiOnce()
    }

    // onResume メソッドを以下のように修正
    override fun onResume() {
        super.onResume()
        // 表情設定の反映
        loadAndApplyEmotionSetting()

        // レシーバーの再登録（確実に受信するため）
        if (localUserId != -1) {
            LocalBroadcastManager.getInstance(this)
                .registerReceiver(speedUpdateReceiver, IntentFilter(LocationTrackingService.BROADCAST_SPEED_UPDATE))
        }
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
        // 位置情報サービス停止（これは必要）
        val stopIntent = Intent(this, LocationTrackingService::class.java).apply {
            action = LocationTrackingService.ACTION_STOP
        }
        startService(stopIntent)
        // Bluetoothは「毎回接続型」なので何もしない
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
        Log.d("PaceFace", "③ updateSpeedUI called speed=$speed")
        binding.tvSpeedValue.text = String.format(Locale.getDefault(), "%.1f", speed)
        updateFaceIconBasedOnSpeed(speed)
    }


    private fun updateFaceIconBasedOnSpeed(speed: Float) {
        lifecycleScope.launch {
            // 1. 自動変更設定を確認
            val emojiPrefs = getSharedPreferences(EMOJI_PREFS_NAME, Context.MODE_PRIVATE)
            val isAutoChangeEnabled = emojiPrefs.getBoolean(KEY_AUTO_CHANGE_ENABLED, false)

            if (isAutoChangeEnabled) {
                // 自動変更がONの場合：速度に基づいて表情を決定
                val speedRule = withContext(Dispatchers.IO) {
                    appDatabase.speedRuleDao()
                        .getSpeedRuleForSpeed(localUserId, speed)
                } ?: return@launch

                val emotionId = speedRule.emotionId
                val faceIconResId = when (emotionId) {
                    1 -> R.drawable.impatient_expression
                    2 -> R.drawable.smile_expression
                    3 -> R.drawable.smile_expression
                    4 -> R.drawable.normal_expression
                    5 -> R.drawable.sad_expression
                    else -> R.drawable.normal_expression
                }

                withContext(Dispatchers.Main) {
                    binding.ivFaceIcon.setImageResource(faceIconResId)
                }
                sendEmotionIfChanged(emotionId)
            } else {
                // 自動変更がOFFの場合：保存された固定表情を維持
                val savedTag = emojiPrefs.getString(KEY_SELECTED_EMOJI_TAG, "1") ?: "1"
                withContext(Dispatchers.Main) {
                    applyManualFaceIcon(savedTag)
                }
                // 固定表情のIDを送信（必要に応じて）
                sendEmotionIfChanged(savedTag.toInt())
            }
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

    // SpeedRuleの初期値を設定する
    private fun checkAndInsertDefaultSpeedRules() {
        lifecycleScope.launch(Dispatchers.IO) {
            val existingRules = appDatabase.speedRuleDao().getSpeedRulesForUser(localUserId)
            if (existingRules.isEmpty()) {
                val defaultRules = listOf(
                    SpeedRule(userId = localUserId, minSpeed = 0f, maxSpeed = 3.0f, emotionId = 5),    // Sad
                    SpeedRule(userId = localUserId, minSpeed = 3.0f, maxSpeed = 4.5f, emotionId = 4),  // Neutral
                    SpeedRule(userId = localUserId, minSpeed = 4.5f, maxSpeed = 5.5f, emotionId = 3),  // Happy
                    SpeedRule(userId = localUserId, minSpeed = 5.5f, maxSpeed = 7.0f, emotionId = 2),  // Excited
                    SpeedRule(userId = localUserId, minSpeed = 7.0f, maxSpeed = 999f, emotionId = 1) // Delighted
                )
                appDatabase.speedRuleDao().insertAll(defaultRules)
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

    //ここから12月18日 エラーの原因を聞く
//    @SuppressLint("MissingPermission")
//    private fun sendEmotionToRaspberryPi(emotionId: Int) {
//        val bluetoothManager =
//            getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
//        val bluetoothAdapter = bluetoothManager.adapter
//        val device = bluetoothAdapter.bondedDevices.firstOrNull {
//            it.name == "raspberrypi"
//        } ?: return
//        try {
//            val method = device.javaClass.getMethod(
//                "createRfcommSocket",
//                Int::class.javaPrimitiveType
//            )
//            val socket = method.invoke(device, 1) as BluetoothSocket
//            bluetoothAdapter.cancelDiscovery()
//            socket.connect()
//            val message = "$emotionId\n"
//            socket.outputStream.write(message.toByteArray())
//            socket.outputStream.flush()
//            socket.close()
//        } catch (e: Exception) {
//            Log.e("PaceFace", "Bluetooth送信失敗", e)
//        }
//    }
//
    private fun sendEmotionIfChanged(emotionId: Int) {
        Log.d("PaceFace", "sendEmotionIfChanged called: $emotionId")
        if (lastSentEmotionId == emotionId) return
        lastSentEmotionId = emotionId
        sendEmotion(emotionId)
    }

    //Bluetooth接続用
    @SuppressLint("MissingPermission")
    private fun connectToRaspberryPiOnce() {
        if (piSocket?.isConnected == true) {
            Log.d("PaceFace", "すでにBluetooth接続済み")
            return
        }
        Log.d("PaceFace", "Bluetooth接続開始")
        val bluetoothManager =
            getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter = bluetoothManager.adapter
        val device = bluetoothAdapter.bondedDevices.firstOrNull {
            it.name == "raspberrypi"
        } ?: run {
            Log.e("PaceFace", "ラズパイが見つかりません")
            return
        }
        try {
            // ★ 前に成功した reflection 方式を使う
            val method = device.javaClass
                .getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
            piSocket = method.invoke(device, 1) as BluetoothSocket
            bluetoothAdapter.cancelDiscovery()
            piSocket!!.connect()
            piOutputStream = piSocket!!.outputStream
            Log.d("PaceFace", "Bluetooth接続成功")
        } catch (e: Exception) {
            Log.e("PaceFace", "Bluetooth接続失敗", e)
            piSocket = null
            piOutputStream = null
        }


    }

    private fun sendEmotion(emotionId: Int) {
        try {
            piOutputStream?.write("$emotionId\n".toByteArray())
            piOutputStream?.flush()
        } catch (e: Exception) {
            Log.e("PaceFace", "送信失敗", e)
        }
    }
}