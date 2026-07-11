package com.enabwf.periodic

import java.time.format.DateTimeFormatter

object AnalyticsLabelFormatter {
    private val dayFormatter = DateTimeFormatter.ofPattern("MMM d")
    private val monthFormatter = DateTimeFormatter.ofPattern("MMM yy")
    private val hourFormatter = DateTimeFormatter.ofPattern("MMM d HH:mm")

    fun trendLabels(
        metrics: AnalyticsMetrics
    ): List<String> = metrics.trend.map { point ->
        when (metrics.trendGranularity) {
            AnalyticsTrendGranularity.DAILY,
            AnalyticsTrendGranularity.WEEKLY -> point.periodStart.format(dayFormatter)
            AnalyticsTrendGranularity.MONTHLY -> point.periodStart.format(monthFormatter)
        }
    }

    fun timelineLabels(timeline: AnalyticsTimelineSeries): List<String> =
        timeline.bins.map { bin ->
            when (timeline.binSize) {
                AnalyticsBinSize.HOUR ->
                    bin.start.atTime(bin.startHour ?: 0, 0).format(hourFormatter)
                AnalyticsBinSize.DAY -> bin.start.format(dayFormatter)
                AnalyticsBinSize.WEEK -> bin.start.format(dayFormatter)
                AnalyticsBinSize.MONTH -> bin.start.format(monthFormatter)
            }
        }

    fun topTaskLabels(topTasks: List<AnalyticsTaskRanking>): List<String> =
        topTasks.map { task ->
            if (task.taskName.length <= 12) task.taskName else task.taskName.take(11) + "…"
        }

    fun hourlyLabels(): List<String> = (0..23).map { "%02d:00".format(it) }

    fun dailyLabels(): List<String> = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
}
