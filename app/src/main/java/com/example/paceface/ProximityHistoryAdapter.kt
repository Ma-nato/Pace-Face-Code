package com.example.paceface

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
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
        history = newHistory
        notifyDataSetChanged()
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
            return R.drawable.emotion_button
        }
    }
}
