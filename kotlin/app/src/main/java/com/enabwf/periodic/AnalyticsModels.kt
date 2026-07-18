package com.enabwf.periodic

import java.time.DayOfWeek
import java.time.LocalDate

enum class AnalyticsRangePreset {
    LAST_30_DAYS,
    LAST_90_DAYS,
    LAST_YEAR,
    ALL_TIME,
    CUSTOM
}

data class AnalyticsFilterState(
    val rangePreset: AnalyticsRangePreset,
    val startDate: LocalDate?,
    val endDate: LocalDate,
    val exactTag: String? = null,
    val taskId: Int? = null,
    val includeArchived: Boolean = false,
    val binSize: AnalyticsBinSize? = null
)

data class AnalyticsFilterOptions(
    val tags: List<String> = emptyList(),
    val tasks: List<AnalyticsTaskOption> = emptyList()
)

data class AnalyticsTaskOption(
    val taskId: Int,
    val taskName: String,
    val isActive: Boolean
)

data class AnalyticsSummary(
    val completionCount: Int,
    val eligibleIntervalCount: Int,
    val onScheduleCount: Int,
    val onScheduleRate: Double?
)

enum class AnalyticsTrendGranularity {
    DAILY,
    WEEKLY,
    MONTHLY
}

enum class AnalyticsBinSize {
    HOUR,
    DAY,
    WEEK,
    MONTH
}

data class AnalyticsTrendPoint(
    val periodStart: LocalDate,
    val completionCount: Int
)

enum class AnalyticsAdherenceCategory {
    EARLY,
    ON_SCHEDULE,
    LATE
}

data class AnalyticsAdherenceBucket(
    val category: AnalyticsAdherenceCategory,
    val count: Int,
    val proportion: Double
)

data class AnalyticsTaskRanking(
    val taskId: Int,
    val taskName: String,
    val isActive: Boolean,
    val completionCount: Int,
    val eligibleIntervalCount: Int,
    val onScheduleRate: Double?,
    val medianIntervalMillis: Long?,
    val firstTag: String = AnalyticsTagColors.UNTAGGED
)

data class AnalyticsTagRanking(
    val tag: String,
    val completionCount: Int
)

data class AnalyticsOverviewStats(
    val totalTaskCount: Int,
    val activeTaskCount: Int,
    val daysInRange: Long,
    val averageCompletionsPerDay: Double,
    val topTasks: List<AnalyticsTaskRanking>
)

data class AnalyticsTimelineBin(
    val start: LocalDate,
    val startHour: Int? = null,
    val completionCount: Int
)

data class AnalyticsTimelineSeries(
    val binSize: AnalyticsBinSize,
    val bins: List<AnalyticsTimelineBin>,
    val tagSeries: Map<String, List<Int>>
)

data class AnalyticsHistogramBucket(
    val startRatio: Double,
    val widthRatio: Double,
    val count: Int
)

data class AnalyticsTaskAdherence(
    val taskId: Int,
    val taskName: String,
    val firstTag: String,
    val periodInMillis: Long,
    val meanIntervalMillis: Long,
    val medianIntervalMillis: Long,
    val adherencePercent: Double,
    val intervalCount: Int,
    val minRatio: Double,
    val q1Ratio: Double,
    val medianRatio: Double,
    val q3Ratio: Double,
    val maxRatio: Double,
    val histogram: List<AnalyticsHistogramBucket>,
    val earlyCount: Int = 0,
    val onScheduleCount: Int = 0,
    val lateCount: Int = 0
) {
    val hasBoxPlot: Boolean get() = intervalCount >= AnalyticsCalculator.MIN_BOX_PLOT_INTERVALS
}

data class AnalyticsHourlyPattern(
    val hour: Int,
    val completionCount: Int
)

data class AnalyticsDailyPattern(
    val dayOfWeek: DayOfWeek,
    val completionCount: Int
)

data class AnalyticsHeatmapCell(
    val dayOfWeek: DayOfWeek,
    val hour: Int,
    val completionCount: Int
)

data class AnalyticsPatterns(
    val hourly: List<AnalyticsHourlyPattern>,
    val daily: List<AnalyticsDailyPattern>,
    val heatmap: List<AnalyticsHeatmapCell>
)

data class AnalyticsMetrics(
    val summary: AnalyticsSummary,
    val trendGranularity: AnalyticsTrendGranularity,
    val trend: List<AnalyticsTrendPoint>,
    val adherence: List<AnalyticsAdherenceBucket>,
    val taskRankings: List<AnalyticsTaskRanking>,
    val tagRankings: List<AnalyticsTagRanking>,
    val overview: AnalyticsOverviewStats,
    val timeline: AnalyticsTimelineSeries,
    val taskAdherence: List<AnalyticsTaskAdherence>,
    val patterns: AnalyticsPatterns
) {
    /** Tasks with enough intervals for the top box-plot chart, sorted by median ratio. */
    val taskBoxPlots: List<AnalyticsTaskAdherence>
        get() = taskAdherence.filter { it.hasBoxPlot }
}

sealed interface AnalyticsUiState {
    val filters: AnalyticsFilterState

    data class Loading(
        override val filters: AnalyticsFilterState
    ) : AnalyticsUiState

    data class Empty(
        override val filters: AnalyticsFilterState,
        val options: AnalyticsFilterOptions
    ) : AnalyticsUiState

    data class Content(
        override val filters: AnalyticsFilterState,
        val options: AnalyticsFilterOptions,
        val metrics: AnalyticsMetrics
    ) : AnalyticsUiState

    data class Error(
        override val filters: AnalyticsFilterState,
        val message: String
    ) : AnalyticsUiState
}
