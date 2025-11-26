package com.example.paceface

import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.paceface.databinding.HomeScreenBinding
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.sqrt

class HomeScreenActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var binding: HomeScreenBinding
    private lateinit var sensorManager: SensorManager
    private var linearAccelSensor: Sensor? = null

    // 速度ベクトル (m/s)
    private val velocity = FloatArray(3) { 0f }
    private var lastTimestamp: Long = 0
    private val dateFormatter = SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = HomeScreenBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // SensorManagerと線形加速度センサーを初期化
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        linearAccelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)

        if (linearAccelSensor == null) {
            // センサーが利用できない場合は "N/A" と表示
            binding.tvSpeedValue.text = "N/A"
        }

        setupNavigation()
    }

    private fun setupNavigation() {
        binding.homeButton.setBackgroundColor(ContextCompat.getColor(this, R.color.selected_nav_item_bg))

        binding.homeButton.setOnClickListener {
            // 現在の画面なので何もしない
        }

        binding.passingButton.setOnClickListener {
            val intent = Intent(this, ProximityHistoryScreenActivity::class.java)
            startActivity(intent)
            overridePendingTransition(0, 0)
        }

        binding.historyButton.setOnClickListener {
            val intent = Intent(this, HistoryScreenActivity::class.java)
            startActivity(intent)
            overridePendingTransition(0, 0)
        }

        binding.emotionButton.setOnClickListener {
            // TODO: EmotionScreenActivity.kt を作成し、遷移を実装する
        }

        binding.gearButton.setOnClickListener {
            val intent = Intent(this, UserSettingsScreenActivity::class.java)
            startActivity(intent)
            overridePendingTransition(0, 0)
        }
    }

    override fun onResume() {
        super.onResume()
        // センサーリスナーを登録
        linearAccelSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    override fun onPause() {
        super.onPause()
        // バッテリー消費を防ぐため、センサーリスナーの登録を解除
        sensorManager.unregisterListener(this)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // この実装では使用しない
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_LINEAR_ACCELERATION) {
            if (lastTimestamp == 0L) {
                lastTimestamp = event.timestamp
                return
            }

            // 経過時間を計算 (秒)
            val dt = (event.timestamp - lastTimestamp) / 1_000_000_000.0f
            lastTimestamp = event.timestamp

            val ax = event.values[0]
            val ay = event.values[1]
            val az = event.values[2]

            // 速度を更新
            velocity[0] += ax * dt
            velocity[1] += ay * dt
            velocity[2] += az * dt

            // 加速度が小さい場合（ほぼ停止している場合）、速度を徐々に減衰させる
            val accelerationMagnitude = sqrt(ax * ax + ay * ay + az * az)
            if (accelerationMagnitude < 0.2f) {
                velocity[0] *= 0.9f
                velocity[1] *= 0.9f
                velocity[2] *= 0.9f
            }

            // 速度の大きさ（m/s）を計算
            val currentSpeedMs = sqrt(velocity[0] * velocity[0] + velocity[1] * velocity[1] + velocity[2] * velocity[2])
            // km/h に変換
            val speedKmh = currentSpeedMs * 3.6

            // UIのTextViewを更新
            binding.tvSpeedValue.text = String.format("%.1f km/h", speedKmh)

            // 最終更新日時を更新
            val currentDateTime = Date(System.currentTimeMillis())
            binding.tvLastUpdate.text = "最終更新日時: ${dateFormatter.format(currentDateTime)}"
        }
    }
}
