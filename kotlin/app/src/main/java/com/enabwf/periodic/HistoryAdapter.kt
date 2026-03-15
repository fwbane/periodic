package com.enabwf.periodic

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class HistoryAdapter(private var historyList: List<CompletionHistoryItem> = emptyList()) : RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val taskName: TextView = view.findViewById(R.id.text_history_task_name)
        val date: TextView = view.findViewById(R.id.text_history_date)
        val timeDiff: TextView = view.findViewById(R.id.text_history_time_diff)
        val tags: TextView = view.findViewById(R.id.text_history_tags)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_history, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = historyList[position]
        holder.taskName.text = item.taskName
        
        val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
        holder.date.text = dateFormat.format(item.completionTime)

        if (item.previousCompletionTime != null) {
            val diff = item.completionTime.time - item.previousCompletionTime.time
            val actualDuration = formatDuration(diff)
            
            val periodDiff = diff - item.taskPeriodInMillis
            val diffString = if (periodDiff >= 0) {
                "+${formatDuration(periodDiff)}"
            } else {
                "-${formatDuration(-periodDiff)}"
            }
            
            holder.timeDiff.text = "$actualDuration ($diffString)"
            holder.timeDiff.visibility = View.VISIBLE
        } else {
            holder.timeDiff.text = "First completion"
            holder.timeDiff.visibility = View.VISIBLE
        }

        if (item.tags.isNotEmpty()) {
            val tagList = item.tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            if (tagList.isNotEmpty()) {
                holder.tags.text = tagList.joinToString(", ")
                holder.tags.visibility = View.VISIBLE
            } else {
                holder.tags.visibility = View.GONE
            }
        } else {
            holder.tags.visibility = View.GONE
        }
    }

    override fun getItemCount() = historyList.size

    fun updateList(newList: List<CompletionHistoryItem>) {
        historyList = newList
        notifyDataSetChanged()
    }
    
    fun addToList(newItems: List<CompletionHistoryItem>) {
        val startPos = historyList.size
        historyList = historyList + newItems
        notifyItemRangeInserted(startPos, newItems.size)
    }

    private fun formatDuration(millis: Long): String {
        if (millis <= 0) return "N/A"

        val days = TimeUnit.MILLISECONDS.toDays(millis)
        val hours = TimeUnit.MILLISECONDS.toHours(millis) % 24
        val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60

        val parts = mutableListOf<String>()
        if (days > 0) parts.add("$days day${if (days > 1) "s" else ""}")
        if (hours > 0) parts.add("$hours hour${if (hours > 1) "s" else ""}")
        // show minutes if hours and days are both 0
        if (minutes > 0 && days == 0L && hours == 0L) parts.add("$minutes minute${if (minutes != 1L) "s" else ""}") 

        return if (parts.isEmpty()) "0 minutes" else parts.joinToString(", ")
    }
}
