package com.example.paceface

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.paceface.databinding.BadgeItemBinding
import com.example.paceface.databinding.BadgeScreenBinding
import kotlinx.coroutines.launch

// Data class for displaying badge information in the UI
data class BadgeDisplayInfo(val description: String, val isAchieved: Boolean)

class BadgeScreenActivity : AppCompatActivity() {

    private lateinit var binding: BadgeScreenBinding
    private lateinit var appDatabase: AppDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = BadgeScreenBinding.inflate(layoutInflater)
        setContentView(binding.root)

        appDatabase = AppDatabase.getDatabase(this)

        binding.btnBack.setOnClickListener {
            finish()
        }

        // --- Navigation --- //
        binding.homeButton.setOnClickListener {
            val intent = Intent(this, HomeScreenActivity::class.java)
            startActivity(intent)
            overridePendingTransition(0, 0)
        }

        binding.passingButton.setOnClickListener {
            val intent = Intent(this, ProximityHistoryScreenActivity::class.java)
            startActivity(intent)
            overridePendingTransition(0, 0)
        }

        binding.historyButton.setOnClickListener { // Added this block
            val intent = Intent(this, HistoryScreenActivity::class.java)
            startActivity(intent)
            overridePendingTransition(0, 0)
        }

        binding.gearButton.setOnClickListener {
            val intent = Intent(this, UserSettingsScreenActivity::class.java)
            startActivity(intent)
        }
        // ------------------ //

        loadAndDisplayBadges()
    }

    private fun loadAndDisplayBadges() {
        // Assume you have a way to get the current user's ID
        val currentUserId = 1 // Replace with actual user ID

        lifecycleScope.launch {
            val allBadges = appDatabase.badgeDao().getAllBadges()
            val userBadges = appDatabase.userBadgeDao().getBadgesForUser(currentUserId)
            val userBadgeIds = userBadges.map { it.badgeId }.toSet()

            val badgesToDisplay = allBadges.map { badge ->
                BadgeDisplayInfo(
                    description = badge.description,
                    isAchieved = userBadgeIds.contains(badge.badgeId)
                )
            }
            setupBadgeList(badgesToDisplay)
        }
    }


    private fun setupBadgeList(badgesToDisplay: List<BadgeDisplayInfo>) {
        binding.badgeContainer.removeAllViews() // Clear existing views

        val marginInPx = (8 * resources.displayMetrics.density).toInt()

        for (badgeInfo in badgesToDisplay) {
            val badgeItemBinding = BadgeItemBinding.inflate(LayoutInflater.from(this), binding.badgeContainer, false)

            badgeItemBinding.badgeText.text = badgeInfo.description

            val cardView = badgeItemBinding.root

            if (badgeInfo.isAchieved) {
                // --- Style for achieved badge ---
                cardView.setCardBackgroundColor(Color.WHITE)
                badgeItemBinding.badgeIcon.setColorFilter(Color.WHITE) // White icon
                badgeItemBinding.badgeText.setTextColor(Color.BLACK)
            } else {
                // --- Style for unachieved badge ---
                cardView.setCardBackgroundColor(Color.parseColor("#E0E0E0")) // Light gray
                badgeItemBinding.badgeIcon.setColorFilter(Color.GRAY) // Grayed out icon
                badgeItemBinding.badgeText.setTextColor(Color.GRAY)
            }

            // Set layout parameters with margin
            val layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            layoutParams.bottomMargin = marginInPx
            cardView.layoutParams = layoutParams

            binding.badgeContainer.addView(cardView)
        }
    }
}
