package com.enabwf.periodic

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

object AnalyticsCalculator {
    fun calculate(
        rows: List<AnalyticsCompletionRow>,
        rangeStart: LocalDate?,
        rangeEnd: LocalDate,
        zoneId: ZoneId
    ): AnalyticsMetrics {
        require(rangeStart == null || !rangeStart.isAfter(rangeEnd)) {
            "Analytics range start must not be after its end."
        }

        val datedRows = rows.map { row ->
            row to row.completionTime.toInstant().atZone(zoneId).toLocalDate()
        }.filter { (_, date) ->
            (rangeStart == null || !date.isBefore(rangeStart)) && !date.isAfter(rangeEnd)
        }

        val effectiveStart = rangeStart
            ?: datedRows.minOfOrNull { it.second }
            ?: rangeEnd
        val granularity = granularityFor(effectiveStart, rangeEnd)
        val adherenceCounts = AnalyticsAdherenceCategory.entries.associateWith { 0 }.toMutableMap()

        datedRows.forEach { (row, _) ->
            adherenceCategory(row)?.let { category ->
                adherenceCounts[category] = adherenceCounts.getValue(category) + 1
            }
        }

        val eligibleCount = adherenceCounts.values.sum()
        val onScheduleCount = adherenceCounts.getValue(AnalyticsAdherenceCategory.ON_SCHEDULE)

        return AnalyticsMetrics(
            summary = AnalyticsSummary(
                completionCount = datedRows.size,
                eligibleIntervalCount = eligibleCount,
                onScheduleCount = onScheduleCount,
                onScheduleRate = rate(onScheduleCount, eligibleCount)
            ),
            trendGranularity = granularity,
            trend = buildTrend(datedRows, effectiveStart, rangeEnd, granularity),
            adherence = AnalyticsAdherenceCategory.entries.map { category ->
                val count = adherenceCounts.getValue(category)
                AnalyticsAdherenceBucket(
                    category = category,
                    count = count,
                    proportion = if (eligibleCount == 0) 0.0 else count.toDouble() / eligibleCount
                )
            },
            taskRankings = buildTaskRankings(datedRows.map { it.first }),
            tagRankings = buildTagRankings(datedRows.map { it.first })
        )
    }

    private fun granularityFor(
        start: LocalDate,
        end: LocalDate
    ): AnalyticsTrendGranularity {
        val dayCount = ChronoUnit.DAYS.between(start, end) + 1
        return when {
            dayCount <= 90 -> AnalyticsTrendGranularity.DAILY
            dayCount <= 366 -> AnalyticsTrendGranularity.WEEKLY
            else -> AnalyticsTrendGranularity.MONTHLY
        }
    }

    private fun buildTrend(
        datedRows: List<Pair<AnalyticsCompletionRow, LocalDate>>,
        rangeStart: LocalDate,
        rangeEnd: LocalDate,
        granularity: AnalyticsTrendGranularity
    ): List<AnalyticsTrendPoint> {
        val firstBucket = bucketStart(rangeStart, granularity)
        val counts = datedRows.groupingBy { (_, date) -> bucketStart(date, granularity) }.eachCount()
        return generateSequence(firstBucket) { current -> nextBucket(current, granularity) }
            .takeWhile { !it.isAfter(rangeEnd) }
            .map { start ->
                AnalyticsTrendPoint(
                    periodStart = start,
                    completionCount = counts[start] ?: 0
                )
            }
            .toList()
    }

    private fun bucketStart(
        date: LocalDate,
        granularity: AnalyticsTrendGranularity
    ): LocalDate = when (granularity) {
        AnalyticsTrendGranularity.DAILY -> date
        AnalyticsTrendGranularity.WEEKLY ->
            date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        AnalyticsTrendGranularity.MONTHLY -> date.withDayOfMonth(1)
    }

    private fun nextBucket(
        date: LocalDate,
        granularity: AnalyticsTrendGranularity
    ): LocalDate = when (granularity) {
        AnalyticsTrendGranularity.DAILY -> date.plusDays(1)
        AnalyticsTrendGranularity.WEEKLY -> date.plusWeeks(1)
        AnalyticsTrendGranularity.MONTHLY -> date.plusMonths(1)
    }

    private fun adherenceCategory(
        row: AnalyticsCompletionRow
    ): AnalyticsAdherenceCategory? {
        val previous = row.previousCompletionTime ?: return null
        val interval = row.completionTime.time - previous.time
        if (interval <= 0 || row.taskPeriodInMillis <= 0) return null

        val ratio = interval.toDouble() / row.taskPeriodInMillis.toDouble()
        return when {
            ratio < 0.9 -> AnalyticsAdherenceCategory.EARLY
            ratio <= 1.1 -> AnalyticsAdherenceCategory.ON_SCHEDULE
            else -> AnalyticsAdherenceCategory.LATE
        }
    }

    private fun buildTaskRankings(
        rows: List<AnalyticsCompletionRow>
    ): List<AnalyticsTaskRanking> {
        return rows.groupBy { it.taskId }
            .map { (taskId, taskRows) ->
                val ordered = taskRows.sortedWith(
                    compareBy<AnalyticsCompletionRow> { it.completionTime.time }
                        .thenBy { it.completionId }
                )
                val eligibleCategories = ordered.mapNotNull(::adherenceCategory)
                val intervals = ordered.mapNotNull { row ->
                    row.previousCompletionTime?.let { previous ->
                        (row.completionTime.time - previous.time).takeIf { it > 0 }
                    }
                }
                val first = ordered.first()
                AnalyticsTaskRanking(
                    taskId = taskId,
                    taskName = first.taskName,
                    isActive = first.isActive,
                    completionCount = ordered.size,
                    eligibleIntervalCount = eligibleCategories.size,
                    onScheduleRate = rate(
                        eligibleCategories.count {
                            it == AnalyticsAdherenceCategory.ON_SCHEDULE
                        },
                        eligibleCategories.size
                    ),
                    medianIntervalMillis = median(intervals)
                )
            }
            .sortedWith(
                compareByDescending<AnalyticsTaskRanking> { it.completionCount }
                    .thenByDescending { it.onScheduleRate ?: -1.0 }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.taskName }
                    .thenBy { it.taskId }
            )
    }

    private fun buildTagRankings(
        rows: List<AnalyticsCompletionRow>
    ): List<AnalyticsTagRanking> {
        return rows.asSequence()
            .flatMap { row ->
                row.tags.split(",")
                    .asSequence()
                    .map(String::trim)
                    .filter(String::isNotEmpty)
                    .distinct()
            }
            .groupingBy { it }
            .eachCount()
            .map { (tag, count) -> AnalyticsTagRanking(tag, count) }
            .sortedWith(
                compareByDescending<AnalyticsTagRanking> { it.completionCount }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.tag }
            )
    }

    private fun rate(numerator: Int, denominator: Int): Double? {
        return if (denominator == 0) null else numerator.toDouble() / denominator
    }

    private fun median(values: List<Long>): Long? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) {
            sorted[middle]
        } else {
            sorted[middle - 1] + (sorted[middle] - sorted[middle - 1]) / 2
        }
    }
}
