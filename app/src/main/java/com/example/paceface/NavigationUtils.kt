// app/src/main/java/com/example/paceface/NavigationUtils.kt
package com.example.paceface

import android.content.Intent
import android.graphics.Color
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.toColorInt

object NavigationUtils {
    fun setupCommonNavigation(
        activity: AppCompatActivity,
        currentActivityClass: Class<out AppCompatActivity>,
        homeButton: ImageButton,
        passingButton: ImageButton,
        historyButton: ImageButton,
        emotionButton: ImageButton,
        gearButton: ImageButton
    ) {
        val navButtons = listOf(homeButton, passingButton, historyButton, emotionButton, gearButton)

        fun updateButtonHighlight(selectedButton: ImageButton) {
            navButtons.forEach { button ->
                if (button == selectedButton) {
                    button.setBackgroundColor("#33000000".toColorInt())
                } else {
                    button.setBackgroundColor(Color.TRANSPARENT)
                }
            }
        }

        when (currentActivityClass) {
            HomeScreenActivity::class.java -> updateButtonHighlight(homeButton)
            ProximityHistoryScreenActivity::class.java -> updateButtonHighlight(passingButton)
            HistoryScreenActivity::class.java -> updateButtonHighlight(historyButton)
            ExpressionCustomizationScreenActivity::class.java -> updateButtonHighlight(emotionButton)
            UserSettingsScreenActivity::class.java -> updateButtonHighlight(gearButton)
        }

        homeButton.setOnClickListener { if (currentActivityClass != HomeScreenActivity::class.java) navigateTo(activity, HomeScreenActivity::class.java) }
        passingButton.setOnClickListener { if (currentActivityClass != ProximityHistoryScreenActivity::class.java) navigateTo(activity, ProximityHistoryScreenActivity::class.java) }
        historyButton.setOnClickListener { if (currentActivityClass != HistoryScreenActivity::class.java) navigateTo(activity, HistoryScreenActivity::class.java) }
        emotionButton.setOnClickListener { if (currentActivityClass != ExpressionCustomizationScreenActivity::class.java) navigateTo(activity, ExpressionCustomizationScreenActivity::class.java) }
        gearButton.setOnClickListener { if (currentActivityClass != UserSettingsScreenActivity::class.java) navigateTo(activity, UserSettingsScreenActivity::class.java) }
    }

    private fun <T : AppCompatActivity> navigateTo(activity: AppCompatActivity, activityClass: Class<T>) {
        val intent = Intent(activity, activityClass)
        intent.flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        if (activityClass == HomeScreenActivity::class.java) {
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        activity.startActivity(intent)
        activity.overridePendingTransition(0, 0)
        activity.finish()
    }
}
