package com.enabwf.periodic

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.google.android.material.color.MaterialColors
import com.google.android.material.progressindicator.LinearProgressIndicator
import java.util.concurrent.TimeUnit

object AnalyticsAdherenceRowBinder {
    fun bind(container: ViewGroup, items: List<AnalyticsTaskAdherence>) {
        container.removeAllViews()
        val inflater = LayoutInflater.from(container.context)
        items.forEach { item ->
            val row = inflater.inflate(R.layout.item_analytics_adherence, container, false)
            bindRow(row, item)
            container.addView(row)
        }
    }

    fun bindRow(itemView: View, item: AnalyticsTaskAdherence) {
        val name = itemView.findViewById<TextView>(R.id.adherence_task_name)
        val summary = itemView.findViewById<TextView>(R.id.adherence_summary)
        val counts = itemView.findViewById<TextView>(R.id.adherence_counts)
        val progress = itemView.findViewById<LinearProgressIndicator>(R.id.adherence_progress)

        val context = itemView.context
        name.text = item.taskName
        summary.text = context.getString(
            R.string.analytics_adherence_row_summary,
            item.adherencePercent,
            formatDuration(item.medianIntervalMillis),
            formatDuration(item.periodInMillis)
        )
        counts.text = context.getString(
            R.string.analytics_adherence_row_counts,
            item.earlyCount,
            item.onScheduleCount,
            item.lateCount
        )
        progress.max = 100
        progress.progress = item.adherencePercent.toInt().coerceIn(0, 100)
        progress.setIndicatorColor(adherenceColor(context, item.adherencePercent))
    }

    private fun adherenceColor(context: android.content.Context, percent: Double): Int {
        val attr = when {
            percent >= 80 -> com.google.android.material.R.attr.colorPrimary
            percent >= 50 -> com.google.android.material.R.attr.colorTertiary
            else -> com.google.android.material.R.attr.colorError
        }
        return MaterialColors.getColor(context, attr, 0)
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
