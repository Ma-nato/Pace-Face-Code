//ProximityHistoryAdapter.kt
package com.example.paceface

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil // 追加
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Locale

class ProximityHistoryAdapter(private var history: List<ProximityHistoryItem>) : RecyclerView.Adapter<ProximityHistoryAdapter.ViewHolder>() {

    private val timeFormatter = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_proximity_history, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = history[position]
        holder.bind(item)
    }

    override fun getItemCount(): Int = history.size

    fun updateData(newHistory: List<ProximityHistoryItem>) {
        val diffCallback = ProximityHistoryDiffCallback(this.history, newHistory)
        val diffResult = DiffUtil.calculateDiff(diffCallback)

        this.history = newHistory
        diffResult.dispatchUpdatesTo(this)
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val emotionIcon: ImageView = itemView.findViewById(R.id.iv_emotion)
        private val username: TextView = itemView.findViewById(R.id.tv_username)
        private val dateTime: TextView = itemView.findViewById(R.id.tv_datetime)
        private val newLabel: TextView = itemView.findViewById(R.id.tv_new_label)

        fun bind(item: ProximityHistoryItem) {
            emotionIcon.setImageResource(getEmotionResource(item.passedUserEmotionId))

            username.text = item.passedUserName
            dateTime.text = timeFormatter.format(item.timestamp)

            if (!item.isConfirmed) {
                newLabel.visibility = View.VISIBLE
            } else {
                newLabel.visibility = View.GONE
            }
        }

        private fun getEmotionResource(emotionId: Int): Int {
            return when (emotionId) {
                1 -> R.drawable.normal_expression
                2 -> R.drawable.troubled_expression
                3 -> R.drawable.impatient_expression
                4 -> R.drawable.smile_expression
                5 -> R.drawable.sad_expression
                6 -> R.drawable.angry_expression
                7 -> R.drawable.sleep_expression
                8 -> R.drawable.wink_expression
                9 -> R.drawable.smug_expression
                else -> R.drawable.normal_expression
            }
        }
    }
}

// DiffUtil.Callback の実装
class ProximityHistoryDiffCallback(private val oldList: List<ProximityHistoryItem>, private val newList: List<ProximityHistoryItem>) : DiffUtil.Callback() {

    override fun getOldListSize(): Int = oldList.size

    override fun getNewListSize(): Int = newList.size

    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        // ここでは便宜的に timestamp をユニークIDとしていますが、
        // 実際のアプリでは各アイテムが持つユニークなIDを使用するべきです。
        // 例えば、データベースから取得したエンティティにIDがある場合、それを比較します。
        return oldList[oldItemPosition].timestamp == newList[newItemPosition].timestamp
    }

    override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        // アイテムの内容が同じかどうかを比較します。
        // すべてのプロパティを比較するか、表示に影響するプロパティのみを比較します。
        return oldList[oldItemPosition] == newList[newItemPosition]
    }
}