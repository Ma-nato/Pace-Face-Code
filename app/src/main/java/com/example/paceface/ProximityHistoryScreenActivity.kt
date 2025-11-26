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

        appDatabase = AppDatabase.getDatabase(this)

        binding.passingButton.setBackgroundColor(ContextCompat.getColor(this, R.color.selected_nav_item_bg))

        setupRecyclerView()
        setupButtons()
        loadProximityHistory()
    }

    private fun setupRecyclerView() {
        adapter = ProximityHistoryAdapter(emptyList())
        binding.rvProximityHistory.layoutManager = LinearLayoutManager(this)
        binding.rvProximityHistory.adapter = adapter
    }

    private fun setupButtons() {
        binding.btnBadgeList.setOnClickListener {
            val intent = Intent(this, BadgeScreenActivity::class.java)
            startActivity(intent)
        }

        binding.homeButton.setOnClickListener {
            val intent = Intent(this, HomeScreenActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
            startActivity(intent)
        }

        binding.passingButton.setOnClickListener {
            // Already on this screen, no action needed.
        }

        binding.historyButton.setOnClickListener {
            val intent = Intent(this, HistoryScreenActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
            startActivity(intent)
        }

        // EmotionScreenActivity is not created yet, so this is commented out.
//        binding.emotionButton.setOnClickListener {
//            val intent = Intent(this, EmotionScreenActivity::class.java)
//            intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
//            startActivity(intent)
//        }

        binding.gearButton.setOnClickListener {
            val intent = Intent(this, UserSettingsScreenActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
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
