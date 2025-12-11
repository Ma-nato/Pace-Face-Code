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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

class HomeScreenActivity : AppCompatActivity() {

    private lateinit var binding: HomeScreenBinding
    private lateinit var appDatabase: AppDatabase
    private var loggedInUserId: Int = -1

    // SharedPreferencesから表情設定を読み込むためのキー
    private val EMOJI_PREFS_NAME = "EmojiPrefs"
    private val KEY_SELECTED_EMOJI_TAG = "selectedEmojiTag"
    private val KEY_AUTO_CHANGE_ENABLED = "autoChangeEnabled"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = HomeScreenBinding.inflate(layoutInflater)
        setContentView(binding.root)

        appDatabase = AppDatabase.getDatabase(this)

        // ログインユーザーIDを取得
        val appPrefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        loggedInUserId = appPrefs.getInt("LOGGED_IN_USER_ID", -1)

        if (loggedInUserId == -1) {
            Toast.makeText(this, "ログインしていません。", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // UIの初期設定
        setupChart()
        loadTodayHistory()
        setupFooterNavigation()
    }

    override fun onResume() {
        super.onResume()
        // 画面が表示されるたびに、保存された設定を読み込んでUIに反映する
        loadAndApplyEmotionSetting()
    }

    private fun loadAndApplyEmotionSetting() {
        val emojiPrefs = getSharedPreferences(EMOJI_PREFS_NAME, Context.MODE_PRIVATE)
        val isAutoChangeEnabled = emojiPrefs.getBoolean(KEY_AUTO_CHANGE_ENABLED, false)

        if (isAutoChangeEnabled) {
            // TODO: 自動変更がONの場合のロジックをここに実装します
            // 例: 現在の速度を取得し、それに応じた表情を表示する
            // updateFaceIconBasedOnSpeed() // この関数を別途実装する必要があります
            binding.tvStatus.text = "自動変更ON"
            // 自動変更モード中は、手動で選択した表情は表示しない、またはデフォルト表示にする
            // binding.ivFaceIcon.setImageResource(R.drawable.default_face) // 例：デフォルト画像
        } else {
            // 固定（手動）モードの場合、保存された表情を表示
            val savedTag = emojiPrefs.getString(KEY_SELECTED_EMOJI_TAG, "1") ?: "1"
            updateFaceIcon(savedTag)
            binding.tvStatus.text = "表情固定中"
        }
    }

    // 速度に応じて表情を更新する関数（TODO: 実装が必要）
    private fun updateFaceIconBasedOnSpeed() {
        // ここで、LocationTrackingServiceなどから速度情報を取得する処理を実装します。
        // 取得した速度に基づいて、適切な表情IDに変換し、updateFaceIconを呼び出します。
        // 例:
        // val currentSpeed = getCurrentSpeed()
        // val emotionId = getEmotionIdForSpeed(currentSpeed)
        // updateFaceIcon(emotionId.toString())
    }

    private fun updateFaceIcon(emotionIdTag: String) {
        val drawableId = when (emotionIdTag) {
            "1" -> R.drawable.normal_expression
            "2" -> R.drawable.troubled_expression
            "3" -> R.drawable.impatient_expression
            "4" -> R.drawable.smile_expression
            "5" -> R.drawable.sad_expression
            "6" -> R.drawable.angry_expression
            else -> R.drawable.normal_expression // デフォルト
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
            val historyList = appDatabase.historyDao().getHistoryForUserOnDate(loggedInUserId, startOfDay, endOfDay)

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
