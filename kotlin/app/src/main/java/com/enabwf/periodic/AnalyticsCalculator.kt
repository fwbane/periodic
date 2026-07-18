package com.enabwf.periodic

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

object AnalyticsCalculator {
    const val MIN_BOX_PLOT_INTERVALS = 4
    const val HISTOGRAM_BUCKET_WIDTH_RATIO = 0.05
    /** Y-axis ceiling for the schedule-adherence box plot (interval / period). */
    const val BOX_PLOT_MAX_RATIO = 5.0
    /** Max high outliers omitted from per-task histograms. */
    const val MAX_OUTLIERS_DROPPED = 2
    private const val MIN_INTERVALS_FOR_OUTLIER_TRIM = 5

    fun calculate(
        rows: List<AnalyticsCompletionRow>,
        rangeStart: LocalDate?,
        rangeEnd: LocalDate,
        zoneId: ZoneId,
        tasks: List<Task> = emptyList(),
        requestedBinSize: AnalyticsBinSize? = null,
        @Suppress("UNUSED_PARAMETER")
        adherenceRows: List<AnalyticsCompletionRow>? = null
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
        val binSize = requestedBinSize ?: granularity.toBinSize()
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
            tagRankings = buildTagRankings(datedRows.map { it.first }),
            overview = buildOverviewStats(datedRows.map { it.first }, tasks, effectiveStart, rangeEnd),
            timeline = buildTimeline(datedRows, effectiveStart, rangeEnd, binSize, zoneId),
            taskAdherence = buildTaskAdherence(datedRows.map { it.first }, tasks),
            patterns = buildPatterns(datedRows, zoneId)
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

    private fun AnalyticsTrendGranularity.toBinSize(): AnalyticsBinSize = when (this) {
        AnalyticsTrendGranularity.DAILY -> AnalyticsBinSize.DAY
        AnalyticsTrendGranularity.WEEKLY -> AnalyticsBinSize.WEEK
        AnalyticsTrendGranularity.MONTHLY -> AnalyticsBinSize.MONTH
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
                    medianIntervalMillis = median(intervals),
                    firstTag = AnalyticsTagColors.firstTag(first.tags)
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

    private fun buildOverviewStats(
        rows: List<AnalyticsCompletionRow>,
        tasks: List<Task>,
        rangeStart: LocalDate,
        rangeEnd: LocalDate
    ): AnalyticsOverviewStats {
        val taskCounts = rows.groupingBy { it.taskId }.eachCount()
        val taskMetadata = rows.associateBy { it.taskId }
        val topTasks = taskCounts.entries
            .sortedByDescending { it.value }
            .take(10)
            .mapNotNull { (taskId, count) ->
                taskMetadata[taskId]?.let { row ->
                    AnalyticsTaskRanking(
                        taskId = taskId,
                        taskName = row.taskName,
                        isActive = row.isActive,
                        completionCount = count,
                        eligibleIntervalCount = 0,
                        onScheduleRate = null,
                        medianIntervalMillis = null,
                        firstTag = AnalyticsTagColors.firstTag(row.tags)
                    )
                }
            }
        val daysInRange = ChronoUnit.DAYS.between(rangeStart, rangeEnd) + 1
        return AnalyticsOverviewStats(
            totalTaskCount = tasks.size,
            activeTaskCount = tasks.count(Task::isActive),
            daysInRange = daysInRange,
            averageCompletionsPerDay = if (daysInRange > 0) rows.size.toDouble() / daysInRange else 0.0,
            topTasks = topTasks
        )
    }

    private fun buildTimeline(
        datedRows: List<Pair<AnalyticsCompletionRow, LocalDate>>,
        rangeStart: LocalDate,
        rangeEnd: LocalDate,
        binSize: AnalyticsBinSize,
        zoneId: ZoneId
    ): AnalyticsTimelineSeries {
        val effectiveBinSize = resolveTimelineBinSize(binSize, rangeStart, rangeEnd)
        val binKeys = timelineBinKeys(rangeStart, rangeEnd, effectiveBinSize, zoneId)
        val totalCounts = datedRows.groupingBy { (row, date) ->
            timelineBucketKey(row, date, effectiveBinSize, zoneId)
        }.eachCount()
        val tagCounts = mutableMapOf<String, MutableMap<Pair<LocalDate, Int?>, Int>>()
        datedRows.forEach { (row, date) ->
            val bucket = timelineBucketKey(row, date, effectiveBinSize, zoneId)
            val tags = row.tags.split(",").map(String::trim).filter(String::isNotEmpty)
                .ifEmpty { listOf("Untagged") }
            tags.distinct().forEach { tag ->
                tagCounts.getOrPut(tag) { mutableMapOf() }[bucket] =
                    tagCounts.getOrPut(tag) { mutableMapOf() }.getOrDefault(bucket, 0) + 1
            }
        }
        val orderedTags = tagCounts.entries
            .sortedByDescending { (_, counts) -> counts.values.sum() }
        val visibleTags = orderedTags.take(8)
        val hiddenTags = orderedTags.drop(8)
        val series = linkedMapOf<String, List<Int>>()
        visibleTags.forEach { (tag, counts) ->
            series[tag] = binKeys.map { counts[it] ?: 0 }
        }
        if (hiddenTags.isNotEmpty()) {
            series["Other"] = binKeys.map { key ->
                hiddenTags.sumOf { (_, counts) -> counts[key] ?: 0 }
            }
        }
        return AnalyticsTimelineSeries(
            binSize = effectiveBinSize,
            bins = binKeys.map { key ->
                AnalyticsTimelineBin(
                    start = key.first,
                    startHour = key.second,
                    completionCount = totalCounts[key] ?: 0
                )
            },
            tagSeries = series
        )
    }

    private fun timelineBinKeys(
        rangeStart: LocalDate,
        rangeEnd: LocalDate,
        binSize: AnalyticsBinSize,
        zoneId: ZoneId
    ): List<Pair<LocalDate, Int?>> = when (binSize) {
        AnalyticsBinSize.HOUR -> {
            val start = rangeStart.atStartOfDay(zoneId)
            val end = rangeEnd.plusDays(1).atStartOfDay(zoneId)
            generateSequence(start) { it.plusHours(1) }
                .takeWhile { it.isBefore(end) }
                .map { it.toLocalDate() to it.hour }
                .toList()
        }
        else -> {
            val first = timelineBucketStart(rangeStart, binSize)
            generateSequence(first) { timelineNextBucket(it, binSize) }
                .takeWhile { !it.isAfter(rangeEnd) }
                .map { timelineBucketKey(it, binSize) }
                .toList()
        }
    }

    private fun resolveTimelineBinSize(
        binSize: AnalyticsBinSize,
        rangeStart: LocalDate,
        rangeEnd: LocalDate
    ): AnalyticsBinSize {
        val dayCount = ChronoUnit.DAYS.between(rangeStart, rangeEnd) + 1
        return when {
            binSize == AnalyticsBinSize.HOUR && dayCount > 14 -> AnalyticsBinSize.DAY
            else -> binSize
        }
    }

    private fun timelineBucketStart(date: LocalDate, binSize: AnalyticsBinSize): LocalDate = when (binSize) {
        AnalyticsBinSize.HOUR, AnalyticsBinSize.DAY -> date
        AnalyticsBinSize.WEEK -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        AnalyticsBinSize.MONTH -> date.withDayOfMonth(1)
    }

    private fun timelineNextBucket(date: LocalDate, binSize: AnalyticsBinSize): LocalDate = when (binSize) {
        AnalyticsBinSize.HOUR, AnalyticsBinSize.DAY -> date.plusDays(1)
        AnalyticsBinSize.WEEK -> date.plusWeeks(1)
        AnalyticsBinSize.MONTH -> date.plusMonths(1)
    }

    private fun timelineBucketKey(
        row: AnalyticsCompletionRow,
        date: LocalDate,
        binSize: AnalyticsBinSize,
        zoneId: ZoneId
    ): Pair<LocalDate, Int?> = when (binSize) {
        AnalyticsBinSize.HOUR -> {
            val local = row.completionTime.toInstant().atZone(zoneId)
            local.toLocalDate() to local.hour
        }
        else -> timelineBucketStart(date, binSize) to null
    }

    private fun timelineBucketKey(date: LocalDate, binSize: AnalyticsBinSize): Pair<LocalDate, Int?> =
        timelineBucketStart(date, binSize) to null

    private fun buildTaskAdherence(
        rows: List<AnalyticsCompletionRow>,
        tasks: List<Task>
    ): List<AnalyticsTaskAdherence> {
        if (rows.isEmpty()) return emptyList()

        val taskById = tasks.associateBy { it.id }
        return rows.groupBy { it.taskId }.mapNotNull { (taskId, taskRows) ->
            val ordered = taskRows.sortedWith(
                compareBy<AnalyticsCompletionRow> { it.completionTime.time }
                    .thenBy { it.completionId }
            )
            val task = taskById[taskId]
            val period = task?.periodInMillis?.takeIf { it > 0L }
                ?: task?.medianRecentPeriod?.takeIf { it > 0L }
                ?: task?.medianHistoryPeriod?.takeIf { it > 0L }
                ?: ordered.first().taskPeriodInMillis.takeIf { it > 0L }
                ?: return@mapNotNull null

            var intervals = ordered.mapNotNull { row ->
                row.previousCompletionTime?.let { previous ->
                    (row.completionTime.time - previous.time).takeIf { it > 0L }
                }
            }
            if (intervals.isEmpty() && ordered.size >= 2) {
                intervals = ordered.zipWithNext { earlier, later ->
                    later.completionTime.time - earlier.completionTime.time
                }.filter { it > 0 }
            }
            if (intervals.isEmpty()) return@mapNotNull null

            val ratios = intervals.map { it.toDouble() / period.toDouble() }.sorted()
            val median = median(intervals) ?: return@mapNotNull null
            val mean = intervals.sum() / intervals.size
            val medianRatio = median.toDouble() / period.toDouble()
            val early = ratios.count { it < 0.9 }
            val onSchedule = ratios.count { it in 0.9..1.1 }
            val late = ratios.size - early - onSchedule
            AnalyticsTaskAdherence(
                taskId = taskId,
                taskName = task?.name ?: ordered.first().taskName,
                firstTag = AnalyticsTagColors.firstTag(ordered.first().tags),
                periodInMillis = period,
                meanIntervalMillis = mean,
                medianIntervalMillis = median,
                adherencePercent = minOf(1.0, 1.0 / maxOf(medianRatio, 1.0 / medianRatio)) * 100,
                intervalCount = intervals.size,
                minRatio = ratios.first(),
                q1Ratio = percentile(ratios, 0.25),
                medianRatio = medianRatio,
                q3Ratio = percentile(ratios, 0.75),
                maxRatio = ratios.last(),
                histogram = buildHistogram(ratios),
                earlyCount = early,
                onScheduleCount = onSchedule,
                lateCount = late
            )
        }.sortedWith(
            compareBy<AnalyticsTaskAdherence> { it.medianRatio }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.taskName }
        )
    }

    /**
     * Builds a histogram over interval/period ratios. Up to [MAX_OUTLIERS_DROPPED]
     * high Tukey outliers are omitted so a single long gap doesn't crush the scale.
     */
    private fun buildHistogram(ratios: List<Double>): List<AnalyticsHistogramBucket> {
        if (ratios.isEmpty()) return emptyList()
        val plotted = trimTopOutliers(ratios)
        val width = HISTOGRAM_BUCKET_WIDTH_RATIO
        var minIndex = kotlin.math.floor(plotted.first() / width).toInt()
        var maxIndex = kotlin.math.floor(plotted.last() / width).toInt()
        if (maxIndex < minIndex) maxIndex = minIndex
        // Pad one empty bucket on each side when possible for readability.
        minIndex -= 1
        maxIndex += 1
        val counts = IntArray(maxIndex - minIndex + 1)
        plotted.forEach { ratio ->
            val index = kotlin.math.floor(ratio / width).toInt().coerceIn(minIndex, maxIndex)
            counts[index - minIndex]++
        }
        return counts.indices.map { offset ->
            val bucketIndex = minIndex + offset
            AnalyticsHistogramBucket(
                startRatio = bucketIndex * width,
                widthRatio = width,
                count = counts[offset]
            )
        }
    }

    /**
     * Drops at most [MAX_OUTLIERS_DROPPED] values above the Tukey upper fence
     * (Q3 + 1.5×IQR). Requires enough samples so the fence is meaningful.
     */
    internal fun trimTopOutliers(
        sortedRatios: List<Double>,
        maxDrop: Int = MAX_OUTLIERS_DROPPED
    ): List<Double> {
        if (sortedRatios.size < MIN_INTERVALS_FOR_OUTLIER_TRIM) return sortedRatios
        val q1 = percentile(sortedRatios, 0.25)
        val q3 = percentile(sortedRatios, 0.75)
        val iqr = q3 - q1
        if (iqr <= 0.0) return sortedRatios
        val fence = q3 + 1.5 * iqr
        var result = sortedRatios
        var dropped = 0
        while (
            dropped < maxDrop &&
            result.size > MIN_INTERVALS_FOR_OUTLIER_TRIM - 1 &&
            result.last() > fence
        ) {
            result = result.dropLast(1)
            dropped++
        }
        return result
    }

    private fun percentile(sorted: List<Double>, percentile: Double): Double {
        if (sorted.isEmpty()) return 0.0
        if (sorted.size == 1) return sorted[0]
        val rank = (sorted.size - 1) * percentile
        val lower = kotlin.math.floor(rank).toInt()
        val upper = kotlin.math.ceil(rank).toInt()
        if (lower == upper) return sorted[lower]
        val weight = rank - lower
        return sorted[lower] * (1.0 - weight) + sorted[upper] * weight
    }

    private fun buildPatterns(
        datedRows: List<Pair<AnalyticsCompletionRow, LocalDate>>,
        zoneId: ZoneId
    ): AnalyticsPatterns {
        val hourly = IntArray(24)
        val daily = IntArray(7)
        val heatmap = Array(7) { IntArray(24) }
        datedRows.forEach { (row, date) ->
            val local = row.completionTime.toInstant().atZone(zoneId)
            val hour = local.hour
            val day = local.dayOfWeek.value - 1
            hourly[hour]++
            daily[day]++
            heatmap[day][hour]++
        }
        return AnalyticsPatterns(
            hourly = hourly.indices.map { AnalyticsHourlyPattern(it, hourly[it]) },
            daily = DayOfWeek.entries.map { day ->
                AnalyticsDailyPattern(day, daily[day.value - 1])
            },
            heatmap = DayOfWeek.entries.flatMap { day ->
                (0..23).map { hour ->
                    AnalyticsHeatmapCell(day, hour, heatmap[day.value - 1][hour])
                }
            }
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
