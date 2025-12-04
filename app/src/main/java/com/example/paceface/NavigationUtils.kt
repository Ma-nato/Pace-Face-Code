package com.example.paceface

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.toColorInt

object NavigationUtils {

    /**
     * 共通のナビゲーションバーのセットアップを行います。
     * 現在のActivityに基づいて、対応するナビゲーションボタンをハイライトし、
     * 各ボタンにクリックリスナーを設定して画面遷移を処理します。
     *
     * @param activity 現在のAppCompatActivityインスタンス
     * @param currentActivityClass 現在のActivityのClassオブジェクト
     * @param homeButton ホーム画面へのボタン
     * @param passingButton すれ違い履歴画面へのボタン
     * @param historyButton 履歴画面へのボタン
     * @param emotionButton 感情画面へのボタン
     * @param gearButton 設定画面へのボタン
     */
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

        // 選択されたボタンを強調表示し、他のボタンの強調を解除する共通関数
        fun updateButtonHighlight(selectedButton: ImageButton) {
            navButtons.forEach { button ->
                if (button == selectedButton) {
                    button.setBackgroundColor("#33000000".toColorInt()) // 暗くする色
                } else {
                    button.setBackgroundColor(Color.TRANSPARENT) // 透明 (デフォルトの状態)
                }
            }
        }

        // 初期選択状態を反映
        when (currentActivityClass) {
            HomeScreenActivity::class.java -> updateButtonHighlight(homeButton)
            ProximityHistoryScreenActivity::class.java -> updateButtonHighlight(passingButton)
            HistoryScreenActivity::class.java -> updateButtonHighlight(historyButton)
            ExpressionCustomizationScreenActivity::class.java -> updateButtonHighlight(emotionButton)
            UserSettingsScreenActivity::class.java -> updateButtonHighlight(gearButton)
            else -> {
                // どのナビゲーションボタンにも対応しないActivityの場合のデフォルト処理
                // 例えば、特定のボタンをデフォルトでハイライトしたい場合などに利用
            }
        }

        homeButton.setOnClickListener {
            updateButtonHighlight(homeButton)
            if (currentActivityClass != HomeScreenActivity::class.java) {
                navigateTo(activity, HomeScreenActivity::class.java)
            }
        }

        passingButton.setOnClickListener {
            updateButtonHighlight(passingButton)
            if (currentActivityClass != ProximityHistoryScreenActivity::class.java) {
                navigateTo(activity, ProximityHistoryScreenActivity::class.java)
            }
        }

        historyButton.setOnClickListener {
            updateButtonHighlight(historyButton)
            if (currentActivityClass != HistoryScreenActivity::class.java) {
                navigateTo(activity, HistoryScreenActivity::class.java)
            }
        }

        emotionButton.setOnClickListener {
            updateButtonHighlight(emotionButton)
             if (currentActivityClass != ExpressionCustomizationScreenActivity::class.java) {
                 navigateTo(activity, ExpressionCustomizationScreenActivity::class.java)
             }
        }

        gearButton.setOnClickListener {
            updateButtonHighlight(gearButton)
            if (currentActivityClass != UserSettingsScreenActivity::class.java) {
                navigateTo(activity, UserSettingsScreenActivity::class.java)
            }
        }
    }

    /**
     * 指定されたActivityへ遷移します。
     * @param context 画面遷移を行うContext（通常はActivity自身）
     * @param activityClass 遷移先のActivityのClassオブジェクト
     */
    private fun <T : AppCompatActivity> navigateTo(context: Context, activityClass: Class<T>) {
        val intent = Intent(context, activityClass)
        context.startActivity(intent)
        // Activityの場合のみoverridePendingTransitionを呼び出す
        if (context is AppCompatActivity) {
            context.overridePendingTransition(0, 0)
        }
    }
}
