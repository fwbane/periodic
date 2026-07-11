package com.enabwf.periodic

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
    val includeArchived: Boolean = false
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
    val medianIntervalMillis: Long?
)

data class AnalyticsTagRanking(
    val tag: String,
    val completionCount: Int
)

data class AnalyticsMetrics(
    val summary: AnalyticsSummary,
    val trendGranularity: AnalyticsTrendGranularity,
    val trend: List<AnalyticsTrendPoint>,
    val adherence: List<AnalyticsAdherenceBucket>,
    val taskRankings: List<AnalyticsTaskRanking>,
    val tagRankings: List<AnalyticsTagRanking>
)

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
