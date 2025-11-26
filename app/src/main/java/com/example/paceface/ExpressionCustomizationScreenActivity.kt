package com.example.paceface

import android.os.Bundle
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.paceface.databinding.ExpressionCustomizationScreenBinding

class ExpressionCustomizationScreenActivity : AppCompatActivity() {

    // View Binding を使用してレイアウトファイル内のビューに安全にアクセスします
    private lateinit var binding: ExpressionCustomizationScreenBinding

    // 選択された表情を保持するための変数
    private var selectedEmoji: ImageView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // View Binding を初期化
        binding = ExpressionCustomizationScreenBinding.inflate(layoutInflater)
        // binding.root はレイアウトのルート要素 (ConstraintLayout) を指します
        setContentView(binding.root)

        // 表情アイコンのリストを作成
        val emojiImageViews = listOf(
            binding.emoji1, binding.emoji2, binding.emoji3,
            binding.emoji4, binding.emoji5, binding.emoji6
        )

        // 各ImageViewに、どの絵文字かを識別するための「タグ」を設定します
        // これにより、どのボタンが押されたかを後から簡単に識別できます。
        emojiImageViews.forEachIndexed { index, imageView ->
            imageView.tag = "表情${index + 1}"
        }

        // 各表情アイコンにクリックリスナーを設定
        emojiImageViews.forEach { emoji ->
            emoji.setOnClickListener {
                // itはクリックされたImageViewを指します
                onEmojiSelected(it as ImageView, emojiImageViews)
            }
        }

        // 「変更」ボタンがクリックされたときの処理
        binding.changeBtn.setOnClickListener {
            if (selectedEmoji != null) {
                // タグを使って、どの表情が選択されたかを取得
                val selectedTag = selectedEmoji?.tag?.toString() ?: "不明"
                val message = "表情「${selectedTag}」に変更されました！"
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

                // TODO: ここで実際に表情データを保存する処理を実装します
                // 例: SharedPreferences.edit().putString("selected_emoji_tag", selectedTag).apply()

            } else {
                // 何も選択されていない場合
                Toast.makeText(this, "変更する表情を選択してください", Toast.LENGTH_SHORT).show()
            }
        }

        // スイッチの状態が変更されたときの処理
        binding.changeSwitch.setOnCheckedChangeListener { _, isChecked ->
            val message = if (isChecked) {
                "自動変更がONになりました"
            } else {
                "自動変更がOFFになりました"
            }
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }

        // 画面表示時に、最初の絵文字をデフォルトで選択状態にしておく（任意）
        // この行が不要な場合は削除してください。
        onEmojiSelected(binding.emoji1, emojiImageViews)
    }

    /**
     * 表情アイコンがクリックされたときに呼び出される関数
     * @param selectedView クリックされたImageView
     * @param allEmojis すべての表情ImageViewのリスト
     */
    private fun onEmojiSelected(selectedView: ImageView, allEmojis: List<ImageView>) {
        // ### 修正点：背景のリセット方法 ###
        // すべてのアイコンの選択状態をリセットします。
        allEmojis.forEach { emoji ->
            // `background = null` ではなく、XMLで指定した元の背景に戻します。
            // これをしないと、選択解除されたアイコンの白い円背景が消えてしまいます。
            emoji.setBackgroundResource(R.drawable.emoji_bg)
        }

        // クリックされたアイコンにだけ、選択状態の背景を設定します。
        selectedView.setBackgroundResource(R.drawable.emoji_selected_bg)

        // 選択されたアイコンの参照を保持します。
        selectedEmoji = selectedView
    }
}


