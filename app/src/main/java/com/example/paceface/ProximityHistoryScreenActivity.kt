//ProximityHistoryScreenActivity.kt
package com.example.paceface

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.paceface.databinding.ProximityHistoryScreenBinding
import kotlinx.coroutines.launch

class ProximityHistoryScreenActivity : AppCompatActivity() {

    private lateinit var binding: ProximityHistoryScreenBinding
    private lateinit var adapter: ProximityHistoryAdapter
    private lateinit var appDatabase: AppDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ProximityHistoryScreenBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // アニメーションの適用 (RecyclerViewのみ)
        binding.rvProximityHistory.translationY = 200f
        binding.rvProximityHistory.alpha = 0f

        binding.rvProximityHistory.animate()
            .translationY(0f)
            .alpha(1f)
            .setDuration(500)
            .setStartDelay(200) // 少し遅れて開始
            .start()

        appDatabase = AppDatabase.getDatabase(this)

        // NavigationUtils を使用して共通ナビゲーションをセットアップ
        // 既存の `binding.passingButton.setBackgroundColor(...)` と `setupButtons()` 内のナビゲーションロジックを置き換えます
        NavigationUtils.setupCommonNavigation(
            this,
            ProximityHistoryScreenActivity::class.java, // 現在のActivityのClassを指定
            binding.homeButton,
            binding.passingButton,
            binding.historyButton,
            binding.emotionButton,
            binding.gearButton
        )

        setupRecyclerView()
        // setupButtons() は NavigationUtils で置き換えられるため、ナビゲーション以外のボタンのみを処理する関数を呼び出す
        setupOtherButtons()
        loadProximityHistory()
    }

    private fun setupRecyclerView() {
        adapter = ProximityHistoryAdapter(emptyList())
        binding.rvProximityHistory.layoutManager = LinearLayoutManager(this)
        binding.rvProximityHistory.adapter = adapter
    }

    // ナビゲーション以外のボタンを設定するための新しい関数
    private fun setupOtherButtons() {
        binding.btnBadgeList.setOnClickListener {
            val intent = Intent(this, BadgeScreenActivity::class.java)
            startActivity(intent)
        }
    }

    private fun loadProximityHistory() {
        // TODO: Replace with actual user ID retrieval logic
        val currentUserId = 1

        lifecycleScope.launch {
            var history = appDatabase.proximityDao().getProximityHistoryWithUser(currentUserId)

            // データベースが空の場合、表示確認用のダミーデータを追加
            if (history.isEmpty()) {
                val now = System.currentTimeMillis()
                history = listOf(
                    ProximityHistoryItem(1, 2, "Bob", now - (1000 * 60 * 5), false, null, 4), // 笑顔
                    ProximityHistoryItem(2, 3, "Charlie", now - (1000 * 60 * 60), true, null, 2), // 困惑
                    ProximityHistoryItem(3, 4, "Dave", now - (1000 * 60 * 60 * 24), true, null, 9), // どや顔
                    ProximityHistoryItem(4, 5, "Eve", now - (1000 * 60 * 60 * 2), false, null, 8), // ウィンク
                    ProximityHistoryItem(5, 6, "Frank", now - (1000 * 60 * 60 * 5), true, null, 7) // 睡眠
                )
            }

            runOnUiThread {
                adapter.updateData(history)
                binding.tvCount.text = "すれちがい回数: ${history.size}回"
            }
        }
    }
}