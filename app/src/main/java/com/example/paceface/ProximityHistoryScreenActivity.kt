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
            val history = appDatabase.proximityDao().getProximityHistoryWithUser(currentUserId)
            runOnUiThread {
                adapter.updateData(history)
                binding.tvCount.text = "すれちがい回数: ${history.size}回"
            }
        }
    }
}
