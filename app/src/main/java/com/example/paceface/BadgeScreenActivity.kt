package com.example.paceface

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import com.example.paceface.databinding.BadgeItemBinding
import com.example.paceface.databinding.BadgeScreenBinding

data class Badge(val id: Int, val description: String, val isAchieved: Boolean)

class BadgeScreenActivity : AppCompatActivity() {

    private lateinit var binding: BadgeScreenBinding

    // This would be replaced with data from your database
    private val badges = listOf(
        Badge(1, "すれちがい合計人数\n10人達成", true),
        Badge(2, "すれちがい合計人数\n50人達成", false),
        Badge(3, "すれちがい合計人数\n100人達成", false),
        Badge(4, "１日で10人とすれちがう", true)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = BadgeScreenBinding.inflate(layoutInflater)
        setContentView(binding.root)

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

        binding.gearButton.setOnClickListener {
            val intent = Intent(this, UserSettingsScreenActivity::class.java)
            startActivity(intent)
        }
        // ------------------ //

        setupBadgeList()
    }

    private fun setupBadgeList() {
        binding.badgeContainer.removeAllViews() // Clear existing views

        val marginInPx = (8 * resources.displayMetrics.density).toInt()

        for (badge in badges) {
            val badgeItemBinding = BadgeItemBinding.inflate(LayoutInflater.from(this), binding.badgeContainer, false)

            badgeItemBinding.badgeText.text = badge.description

            val cardView = badgeItemBinding.root

            if (badge.isAchieved) {
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
