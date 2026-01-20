//HomeScreenActivity.kt
package com.example.paceface

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.IBinder
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
import java.util.Date

class HomeScreenActivity : AppCompatActivity() {

    private val SPP_UUID: UUID =
        UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    private var isConnecting = false

    // BluetoothService 関連の変数を追加
    private var bluetoothService: BluetoothService? = null
    private var isBound = false

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

    // サービス接続の管理
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as BluetoothService.BluetoothBinder
            bluetoothService = binder.getService()
            isBound = true
            Log.d("PaceFace", "BluetoothService connected")
            // 接続を開始
            bluetoothService?.connectToRaspberryPi()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            bluetoothService = null
            isBound = false
            Log.d("PaceFace", "BluetoothService disconnected")
        }
    }

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

        // BluetoothService を開始してバインド
        val intent = Intent(this, BluetoothService::class.java)
        startService(intent) // 画面遷移してもサービスが死なないように startService も呼ぶ
        bindService(intent, connection, Context.BIND_AUTO_CREATE)

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
        checkAndInsertDefaultSpeedRules()
        setupNavigation()
        setupChart()
        loadTodayHistory()
        setupFooterNavigationIfExists()

        checkPermissionsAndStartService()
        startChartUpdateLoop()
        LocalBroadcastManager.getInstance(this)
            .registerReceiver(speedUpdateReceiver, IntentFilter(LocationTrackingService.BROADCAST_SPEED_UPDATE))

        loadAndApplyEmotionSetting()
    }

    override fun onResume() {
        super.onResume()
        loadAndApplyEmotionSetting()

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
        // 位置情報サービス停止
        val stopIntent = Intent(this, LocationTrackingService::class.java).apply {
            action = LocationTrackingService.ACTION_STOP
        }
        startService(stopIntent)

        // サービスのアンバインド
        if (isBound) {
            unbindService(connection)
            isBound = false
        }
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
            val emojiPrefs = getSharedPreferences(EMOJI_PREFS_NAME, Context.MODE_PRIVATE)
            val isAutoChangeEnabled = emojiPrefs.getBoolean(KEY_AUTO_CHANGE_ENABLED, false)

            if (isAutoChangeEnabled) {
                val speedRule = withContext(Dispatchers.IO) {
                    appDatabase.speedRuleDao()
                        .getSpeedRuleForSpeed(localUserId, speed)
                } ?: return@launch

                val emotionId = speedRule.emotionId
                val faceIconResId = getDrawableIdForEmotion(emotionId.toString())

                withContext(Dispatchers.Main) {
                    binding.ivFaceIcon.setImageResource(faceIconResId)
                }
            }
        }
    }

    private fun loadAndApplyEmotionSetting() {
        val emojiPrefs = getSharedPreferences(EMOJI_PREFS_NAME, Context.MODE_PRIVATE)
        val isAutoChangeEnabled = emojiPrefs.getBoolean(KEY_AUTO_CHANGE_ENABLED, false)

        if (!isAutoChangeEnabled) {
            val savedTag = emojiPrefs.getString(KEY_SELECTED_EMOJI_TAG, "1") ?: "1"
            val faceIconResId = getDrawableIdForEmotion(savedTag)
            binding.ivFaceIcon.setImageResource(faceIconResId)
        }
    }

    private fun getDrawableIdForEmotion(tag: String): Int {
        return when (tag) {
            "1" -> R.drawable.normal_expression
            "2" -> R.drawable.troubled_expression
            "3" -> R.drawable.impatient_expression
            "4" -> R.drawable.smile_expression
            "5" -> R.drawable.sad_expression
            "6" -> R.drawable.angry_expression
            "7" -> R.drawable.sleep_expression
            "8" -> R.drawable.wink_expression
            "9" -> R.drawable.smug_expression
            else -> R.drawable.sleep_expression
        }
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

    private fun setupFooterNavigationIfExists() {
        // 必要に応じて実装
    }

    private fun setupChart() {
        binding.lineChart.description.isEnabled = false
        binding.lineChart.setTouchEnabled(true)
        binding.lineChart.isDragEnabled = true
        binding.lineChart.setScaleEnabled(true)
        binding.lineChart.setPinchZoom(true)
    }

    private fun loadTodayHistory() {
        lifecycleScope.launch {
            updateChartWithDataWindow()
        }
    }

    private suspend fun updateChartWithDataWindow() {
        withContext(Dispatchers.IO) {
            val historyList = appDatabase.historyDao().getRecentHistory(localUserId, 10)
            withContext(Dispatchers.Main) {
                if (historyList.isEmpty()) {
                    binding.lineChart.clear()
                    binding.lineChart.invalidate()
                } else {
                    updateChart(historyList)
                }
                // グラフ更新のタイミングで最終更新日時を更新
                updateLastUpdateTime()
            }
        }
    }

    private fun updateChart(historyList: List<History>) {
        val entries = historyList.mapIndexed { index, history ->
            Entry(index.toFloat(), history.walkingSpeed)
        }
        val dataSet = LineDataSet(entries, "Walking Speed")
        dataSet.color = Color.BLUE
        dataSet.valueTextColor = Color.BLACK
        val lineData = LineData(dataSet)
        binding.lineChart.data = lineData
        binding.lineChart.invalidate()
    }

    private fun updateLastUpdateTime() {
        val now = Date()
        val formattedDate = dateFormatter.format(now)
        binding.tvLastUpdate.text = "最終更新日時：$formattedDate"
    }

    private fun checkAndInsertDefaultSpeedRules() {
        lifecycleScope.launch(Dispatchers.IO) {
            val existingRules = appDatabase.speedRuleDao().getSpeedRulesForUser(localUserId)
            if (existingRules.isEmpty()) {
                val defaultRules = listOf(
                    SpeedRule(userId = localUserId, minSpeed = 0f, maxSpeed = 1f, emotionId = 7),
                    SpeedRule(userId = localUserId, minSpeed = 1f, maxSpeed = 3.0f, emotionId = 5),
                    SpeedRule(userId = localUserId, minSpeed = 3.0f, maxSpeed = 4.5f, emotionId = 1),
                    SpeedRule(userId = localUserId, minSpeed = 4.5f, maxSpeed = 5.5f, emotionId = 4),
                    SpeedRule(userId = localUserId, minSpeed = 5.5f, maxSpeed = 7.0f, emotionId = 2),
                    SpeedRule(userId = localUserId, minSpeed = 7.0f, maxSpeed = 9.0f, emotionId = 3),
                    SpeedRule(userId = localUserId, minSpeed = 9.0f, maxSpeed = 999f, emotionId = 6)
                )
                appDatabase.speedRuleDao().insertAll(defaultRules)
            }
        }
    }

    private fun sendEmotionIfChanged(emotionId: Int) {
        if (lastSentEmotionId == emotionId) return
        lastSentEmotionId = emotionId
        sendEmotion(emotionId)
    }

    private fun sendEmotion(emotionId: Int) {
        bluetoothService?.sendEmotion(emotionId)
    }

    // 古い接続メソッドは削除またはコメントアウト
    private fun connectToRaspberryPiOnce() {
        // BluetoothService側で処理するため、ここでは何もしない
    }
}