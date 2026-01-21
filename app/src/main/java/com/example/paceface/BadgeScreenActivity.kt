//BadgeScreenActivity.kt
package com.example.paceface

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
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

        binding.backButton.setOnClickListener {
            finish()
        }

        // --- Navigation --- //
        // NavigationUtils を使用して共通ナビゲーションをセットアップ
        NavigationUtils.setupCommonNavigation(
            this,
            BadgeScreenActivity::class.java, // このActivityはナビゲーションバーの主要な画面ではないため、どれもハイライトされない
            binding.homeButton,
            binding.passingButton,
            binding.historyButton,
            binding.emotionButton,
            binding.gearButton
        )
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
                // メインカラー（青）を背景にし、テキストとアイコンを白にして強調
                cardView.setCardBackgroundColor(ContextCompat.getColor(this, R.color.colorPrimary))
                badgeItemBinding.badgeIcon.setColorFilter(Color.WHITE)
                badgeItemBinding.badgeText.setTextColor(Color.WHITE)
                cardView.cardElevation = 8f // 達成済みは少し浮かせる
            } else {
                // --- Style for unachieved badge ---
                cardView.setCardBackgroundColor(Color.parseColor("#F5F5F5")) // より薄いグレー
                badgeItemBinding.badgeIcon.setColorFilter(Color.parseColor("#BDBDBD")) // 薄いグレーのアイコン
                badgeItemBinding.badgeText.setTextColor(Color.parseColor("#9E9E9E"))
                cardView.cardElevation = 0f // 未達成は平坦に
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