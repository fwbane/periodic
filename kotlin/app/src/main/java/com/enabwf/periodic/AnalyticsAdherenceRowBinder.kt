package com.enabwf.periodic

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView

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
        val histogram = itemView.findViewById<IntervalHistogramView>(R.id.adherence_histogram)

        val context = itemView.context
        name.text = item.taskName
        summary.text = context.getString(
            R.string.analytics_adherence_row_summary,
            item.adherencePercent,
            AnalyticsChartConfigurator.formatDuration(item.medianIntervalMillis),
            AnalyticsChartConfigurator.formatDuration(item.periodInMillis)
        )
        histogram.setBuckets(item.histogram, item.periodInMillis)
    }
}
