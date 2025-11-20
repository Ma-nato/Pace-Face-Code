package com.example.paceface

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TableRow
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.paceface.databinding.ProximityHistoryScreenBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ProximityHistoryScreenActivity : AppCompatActivity() {

    private lateinit var binding: ProximityHistoryScreenBinding
    private val sharedPreferences by lazy { getSharedPreferences("PaceFace_ProximityHistory", Context.MODE_PRIVATE) }

    // 仮のすれちがい履歴データ
    private val proximityHistory = listOf(
        // ここにすれちがい履歴のデータを追加します。例：Pair("2023/11/01", "10:30")
        // 今回はダミーデータとしていくつか追加します。
        Pair(Date(System.currentTimeMillis() - 1000 * 60 * 5), "NEW"), // 5分前
        Pair(Date(System.currentTimeMillis() - 1000 * 60 * 60 * 24), ""), // 1日前
        Pair(Date(System.currentTimeMillis() - 1000 * 60 * 60 * 24 * 2), "") // 2日前
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ProximityHistoryScreenBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 現在の画面に対応するボタンの背景色を変更
        binding.passingButton.setBackgroundColor(ContextCompat.getColor(this, R.color.selected_nav_item_bg))

        binding.homeButton.setOnClickListener {
            val intent = Intent(this, HomeScreenActivity::class.java)
            startActivity(intent)
            overridePendingTransition(0, 0) // 遷移時のアニメーションを無効化
        }

        displayProximityHistory()
    }

    override fun onPause() {
        super.onPause()
        // 画面を離れるときに現在時刻を保存
        sharedPreferences.edit().putLong("last_seen", System.currentTimeMillis()).apply()
    }

    private fun displayProximityHistory() {
        val lastSeen = sharedPreferences.getLong("last_seen", 0)
        binding.tvCount.text = "${proximityHistory.size}人"

        // ヘッダー以外のすべての行を削除
        for (i in binding.tlHistory.childCount - 1 downTo 1) {
            binding.tlHistory.removeViewAt(i)
        }

        val dateFormat = SimpleDateFormat("MM/dd", Locale.getDefault())
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

        for ((date, _) in proximityHistory) {
            val tableRow = TableRow(this)
            tableRow.setBackgroundResource(R.drawable.table_row_bg)

            // 日付
            val dateText = TextView(this)
            dateText.text = dateFormat.format(date)
            dateText.setPadding(8, 8, 8, 8)
            dateText.setTextColor(ContextCompat.getColor(this, android.R.color.black))
            tableRow.addView(dateText)

            // 区切り線1
            val separator1 = View(this)
            separator1.layoutParams = TableRow.LayoutParams(1, TableRow.LayoutParams.MATCH_PARENT)
            separator1.setBackgroundColor(ContextCompat.getColor(this, android.R.color.white))
            tableRow.addView(separator1)

            // 時間
            val timeText = TextView(this)
            timeText.text = timeFormat.format(date)
            timeText.setPadding(8, 8, 8, 8)
            timeText.setTextColor(ContextCompat.getColor(this, android.R.color.black))
            tableRow.addView(timeText)

            // 区切り線2
            val separator2 = View(this)
            separator2.layoutParams = TableRow.LayoutParams(1, TableRow.LayoutParams.MATCH_PARENT)
            separator2.setBackgroundColor(ContextCompat.getColor(this, android.R.color.white))
            tableRow.addView(separator2)

            // NEW表示
            val newText = TextView(this)
            if (date.time > lastSeen) {
                newText.text = "NEW"
            }
            newText.setPadding(8, 8, 8, 8)
            newText.setTextColor(ContextCompat.getColor(this, android.R.color.black))
            tableRow.addView(newText)

            binding.tlHistory.addView(tableRow)
        }
    }
}
