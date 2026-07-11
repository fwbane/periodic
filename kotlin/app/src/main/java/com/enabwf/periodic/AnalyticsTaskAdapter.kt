package com.enabwf.periodic

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import java.util.concurrent.TimeUnit

class AnalyticsTaskAdapter(
    private val onTaskClick: (AnalyticsTaskRanking) -> Unit
) : ListAdapter<AnalyticsTaskRanking, AnalyticsTaskAdapter.TaskViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_analytics_task, parent, false)
        return TaskViewHolder(view, onTaskClick)
    }

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class TaskViewHolder(
        itemView: View,
        private val onTaskClick: (AnalyticsTaskRanking) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        private val name: TextView = itemView.findViewById(R.id.analytics_task_name)
        private val completions: TextView =
            itemView.findViewById(R.id.analytics_task_completions)
        private val cadence: TextView = itemView.findViewById(R.id.analytics_task_cadence)

        fun bind(item: AnalyticsTaskRanking) {
            val context = itemView.context
            val rate = item.onScheduleRate?.let {
                context.getString(R.string.analytics_rate_percent, it * 100)
            } ?: context.getString(R.string.analytics_rate_unavailable)
            name.text = item.taskName
            completions.text = buildString {
                append(context.getString(R.string.analytics_completion_count, item.completionCount))
                append(" · ")
                append(context.getString(R.string.analytics_task_rate, rate))
            }
            cadence.text = context.getString(
                R.string.analytics_task_cadence,
                item.medianIntervalMillis?.let(::formatDuration)
                    ?: context.getString(R.string.analytics_rate_unavailable)
            )
            itemView.contentDescription = context.getString(
                R.string.analytics_task_row_description,
                item.taskName,
                item.completionCount,
                rate
            )
            itemView.setOnClickListener { onTaskClick(item) }
        }

        private fun formatDuration(milliseconds: Long): String {
            val days = TimeUnit.MILLISECONDS.toDays(milliseconds)
            if (days > 0) return "$days day${if (days == 1L) "" else "s"}"
            val hours = TimeUnit.MILLISECONDS.toHours(milliseconds)
            if (hours > 0) return "$hours hour${if (hours == 1L) "" else "s"}"
            val minutes = TimeUnit.MILLISECONDS.toMinutes(milliseconds).coerceAtLeast(1)
            return "$minutes minute${if (minutes == 1L) "" else "s"}"
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<AnalyticsTaskRanking>() {
        override fun areItemsTheSame(
            oldItem: AnalyticsTaskRanking,
            newItem: AnalyticsTaskRanking
        ): Boolean = oldItem.taskId == newItem.taskId

        override fun areContentsTheSame(
            oldItem: AnalyticsTaskRanking,
            newItem: AnalyticsTaskRanking
        ): Boolean = oldItem == newItem
    }
}
