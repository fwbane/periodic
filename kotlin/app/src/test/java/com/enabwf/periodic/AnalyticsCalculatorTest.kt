package com.enabwf.periodic

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class AnalyticsCalculatorTest {
    private val zone: ZoneId = ZoneOffset.UTC

    @Test
    fun emptyDatasetProducesZeroSummaryAndNoRankings() {
        val metrics = calculate(emptyList(), LocalDate.parse("2026-01-01"), LocalDate.parse("2026-01-03"))

        assertEquals(0, metrics.summary.completionCount)
        assertEquals(0, metrics.summary.eligibleIntervalCount)
        assertEquals(0, metrics.summary.onScheduleCount)
        assertNull(metrics.summary.onScheduleRate)
        assertEquals(listOf(0, 0, 0), metrics.trend.map { it.completionCount })
        assertEquals(emptyList<AnalyticsTaskRanking>(), metrics.taskRankings)
        assertEquals(emptyList<AnalyticsTagRanking>(), metrics.tagRankings)
        assertEquals(listOf(0, 0, 0), metrics.adherence.map { it.count })
    }

    @Test
    fun singleCompletionIsExcludedFromAdherence() {
        val metrics = calculate(
            listOf(row(id = 1, completionMillis = instant("2026-01-02"), previousMillis = null)),
            LocalDate.parse("2026-01-01"),
            LocalDate.parse("2026-01-03")
        )

        assertEquals(1, metrics.summary.completionCount)
        assertEquals(0, metrics.summary.eligibleIntervalCount)
        assertNull(metrics.summary.onScheduleRate)
        assertEquals(0, metrics.taskRankings.single().eligibleIntervalCount)
        assertNull(metrics.taskRankings.single().medianIntervalMillis)
    }

    @Test
    fun adherenceUsesInclusiveNinetyAndOneHundredTenPercentEdges() {
        val base = instant("2026-01-02")
        val rows = listOf(
            row(id = 1, completionMillis = base, previousMillis = base - 899, periodMillis = 1_000),
            row(id = 2, completionMillis = base + 2_000, previousMillis = base + 1_100, periodMillis = 1_000),
            row(id = 3, completionMillis = base + 4_000, previousMillis = base + 2_900, periodMillis = 1_000),
            row(id = 4, completionMillis = base + 6_000, previousMillis = base + 4_899, periodMillis = 1_000)
        )

        val metrics = calculate(rows, LocalDate.parse("2026-01-02"), LocalDate.parse("2026-01-02"))

        assertEquals(4, metrics.summary.eligibleIntervalCount)
        assertEquals(2, metrics.summary.onScheduleCount)
        assertEquals(0.5, metrics.summary.onScheduleRate!!, 0.0)
        assertEquals(
            mapOf(
                AnalyticsAdherenceCategory.EARLY to 1,
                AnalyticsAdherenceCategory.ON_SCHEDULE to 2,
                AnalyticsAdherenceCategory.LATE to 1
            ),
            metrics.adherence.associate { it.category to it.count }
        )
    }

    @Test
    fun nonPositiveIntervalsAndPeriodsAreNotEligible() {
        val base = instant("2026-01-02")
        val metrics = calculate(
            listOf(
                row(id = 1, completionMillis = base, previousMillis = base, periodMillis = 1_000),
                row(id = 2, completionMillis = base + 1_000, previousMillis = base, periodMillis = 0)
            ),
            LocalDate.parse("2026-01-02"),
            LocalDate.parse("2026-01-02")
        )

        assertEquals(0, metrics.summary.eligibleIntervalCount)
        assertNull(metrics.summary.onScheduleRate)
    }

    @Test
    fun dateLimitsAreInclusiveAndRowsOutsideRangeAreIgnored() {
        val metrics = calculate(
            listOf(
                row(id = 1, completionMillis = instant("2025-12-31")),
                row(id = 2, completionMillis = instant("2026-01-01")),
                row(id = 3, completionMillis = instant("2026-01-03", hour = 23)),
                row(id = 4, completionMillis = instant("2026-01-04"))
            ),
            LocalDate.parse("2026-01-01"),
            LocalDate.parse("2026-01-03")
        )

        assertEquals(2, metrics.summary.completionCount)
        assertEquals(listOf(1, 0, 1), metrics.trend.map { it.completionCount })
    }

    @Test
    fun granularityChangesAtNinetyAndThreeHundredSixtySixDayBoundaries() {
        assertEquals(
            AnalyticsTrendGranularity.DAILY,
            calculate(emptyList(), LocalDate.parse("2026-01-01"), LocalDate.parse("2026-03-31")).trendGranularity
        )
        assertEquals(
            AnalyticsTrendGranularity.WEEKLY,
            calculate(emptyList(), LocalDate.parse("2026-01-01"), LocalDate.parse("2026-04-01")).trendGranularity
        )
        assertEquals(
            AnalyticsTrendGranularity.WEEKLY,
            calculate(emptyList(), LocalDate.parse("2025-01-01"), LocalDate.parse("2026-01-01")).trendGranularity
        )
        assertEquals(
            AnalyticsTrendGranularity.MONTHLY,
            calculate(emptyList(), LocalDate.parse("2025-01-01"), LocalDate.parse("2026-01-02")).trendGranularity
        )
    }

    @Test
    fun weeklyAndMonthlyBucketsUseCalendarBoundaries() {
        val weekly = calculate(
            listOf(row(id = 1, completionMillis = instant("2026-01-04"))),
            LocalDate.parse("2026-01-01"),
            LocalDate.parse("2026-04-01")
        )
        assertEquals(LocalDate.parse("2025-12-29"), weekly.trend.first().periodStart)
        assertEquals(1, weekly.trend.first().completionCount)

        val monthly = calculate(
            listOf(row(id = 2, completionMillis = instant("2025-01-15"))),
            LocalDate.parse("2025-01-10"),
            LocalDate.parse("2026-01-12")
        )
        assertEquals(LocalDate.parse("2025-01-01"), monthly.trend.first().periodStart)
        assertEquals(1, monthly.trend.first().completionCount)
    }

    @Test
    fun eachCompletionCountsOncePerDistinctTag() {
        val metrics = calculate(
            listOf(
                row(id = 1, completionMillis = instant("2026-01-01"), tags = "home, health, home"),
                row(id = 2, completionMillis = instant("2026-01-02"), tags = "health, work"),
                row(id = 3, completionMillis = instant("2026-01-03"), tags = "  ")
            ),
            LocalDate.parse("2026-01-01"),
            LocalDate.parse("2026-01-03")
        )

        assertEquals(
            listOf(
                AnalyticsTagRanking("health", 2),
                AnalyticsTagRanking("home", 1),
                AnalyticsTagRanking("work", 1)
            ),
            metrics.tagRankings
        )
    }

    @Test
    fun archivedRowsRemainVisibleWhenTheyAreInCalculatorInput() {
        val metrics = calculate(
            listOf(
                row(id = 1, taskId = 1, taskName = "Active", isActive = true, completionMillis = instant("2026-01-01")),
                row(id = 2, taskId = 2, taskName = "Archived", isActive = false, completionMillis = instant("2026-01-02"))
            ),
            LocalDate.parse("2026-01-01"),
            LocalDate.parse("2026-01-02")
        )

        assertEquals(2, metrics.summary.completionCount)
        assertEquals(listOf(true, false), metrics.taskRankings.map { it.isActive })
    }

    @Test
    fun invalidRangeIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            calculate(emptyList(), LocalDate.parse("2026-01-02"), LocalDate.parse("2026-01-01"))
        }
    }

    @Test
    fun emptyDatasetIncludesZeroFilledTimelineBins() {
        val metrics = calculate(emptyList(), LocalDate.parse("2026-01-01"), LocalDate.parse("2026-01-03"))
        assertEquals(3, metrics.timeline.bins.size)
        assertEquals(listOf(0, 0, 0), metrics.timeline.bins.map { it.completionCount })
        assertEquals(0, metrics.overview.totalTaskCount)
        assertEquals(0, metrics.taskAdherence.size)
        assertEquals(24, metrics.patterns.hourly.size)
    }

    @Test
    fun timelineGroupsByTagIncludingUntaggedAndOtherRollup() {
        val metrics = AnalyticsCalculator.calculate(
            rows = listOf(
                row(id = 1, completionMillis = instant("2026-01-01"), tags = "home, health"),
                row(id = 2, completionMillis = instant("2026-01-02"), tags = "work"),
                row(id = 3, completionMillis = instant("2026-01-03"), tags = " ")
            ),
            rangeStart = LocalDate.parse("2026-01-01"),
            rangeEnd = LocalDate.parse("2026-01-03"),
            zoneId = zone,
            requestedBinSize = AnalyticsBinSize.DAY
        )

        assertEquals(
            mapOf("home" to 1, "health" to 1, "work" to 1, "Untagged" to 1),
            metrics.timeline.tagSeries.mapValues { it.value.sum() }
        )
    }

    @Test
    fun perTaskAdherenceUsesMedianRatioFormula() {
        val period = 86_400_000L
        val task = Task(id = 1, name = "Task", periodInMillis = period)
        val base = instant("2026-01-01")
        val metrics = calculate(
            listOf(
                row(id = 1, completionMillis = base, previousMillis = null, periodMillis = period),
                row(
                    id = 2,
                    completionMillis = base + period,
                    previousMillis = base,
                    periodMillis = period
                ),
                row(
                    id = 3,
                    completionMillis = base + 2 * period,
                    previousMillis = base + period,
                    periodMillis = period
                ),
                row(
                    id = 4,
                    completionMillis = base + 3 * period,
                    previousMillis = base + 2 * period,
                    periodMillis = period
                ),
                row(
                    id = 5,
                    completionMillis = base + 4 * period,
                    previousMillis = base + 3 * period,
                    periodMillis = period
                )
            ),
            LocalDate.parse("2026-01-01"),
            LocalDate.parse("2026-01-10"),
            tasks = listOf(task)
        )

        val adherence = metrics.taskAdherence.single()
        assertEquals(100.0, adherence.adherencePercent, 0.01)
        assertEquals(4, adherence.intervalCount)
        assertEquals(4, adherence.onScheduleCount)
        assertEquals(true, adherence.hasBoxPlot)
        assertEquals(1.0, adherence.medianRatio, 0.01)
        assertEquals(true, adherence.histogram.isNotEmpty())
    }

    @Test
    fun histogramDropsUpToTwoHighTukeyOutliers() {
        val cluster = listOf(0.95, 0.97, 0.99, 1.0, 1.01, 1.03, 1.05)
        val ratios = cluster + listOf(4.0, 6.0)
        val trimmed = AnalyticsCalculator.trimTopOutliers(ratios)
        assertEquals(cluster, trimmed)
    }

    @Test
    fun histogramKeepsPointsWhenTooFewForOutlierTrim() {
        val ratios = listOf(1.0, 1.1, 4.0)
        assertEquals(ratios, AnalyticsCalculator.trimTopOutliers(ratios))
    }

    @Test
    fun boxPlotExtentsKeepRawMaxBeforeDisplayClamp() {
        val period = 1_000L
        val base = 10_000L
        val gaps = listOf(950L, 970L, 990L, 1_000L, 1_010L, 1_030L, 1_050L, 4_000L, 6_000L)
        val rows = mutableListOf(
            row(id = 1, completionMillis = base, previousMillis = null, periodMillis = period)
        )
        var previous = base
        gaps.forEachIndexed { index, gap ->
            val next = previous + gap
            rows += row(
                id = index + 2,
                completionMillis = next,
                previousMillis = previous,
                periodMillis = period
            )
            previous = next
        }
        val metrics = calculate(
            rows,
            LocalDate.parse("1970-01-01"),
            LocalDate.parse("1970-01-02")
        )
        val box = metrics.taskBoxPlots.single()
        // Raw stats keep the outlier; the chart clamps display to BOX_PLOT_MAX_RATIO.
        assertEquals(6.0, box.maxRatio, 0.01)
        assertEquals(5.0, AnalyticsCalculator.BOX_PLOT_MAX_RATIO, 0.0)
    }

    @Test
    fun boxPlotHiddenUntilFourIntervals() {
        val period = 1_000L
        val metrics = calculate(
            listOf(
                row(id = 1, completionMillis = 1_000, previousMillis = null, periodMillis = period),
                row(id = 2, completionMillis = 2_000, previousMillis = 1_000, periodMillis = period),
                row(id = 3, completionMillis = 3_000, previousMillis = 2_000, periodMillis = period),
                row(id = 4, completionMillis = 4_000, previousMillis = 3_000, periodMillis = period)
            ),
            LocalDate.parse("1970-01-01"),
            LocalDate.parse("1970-01-02")
        )
        assertEquals(1, metrics.taskAdherence.size)
        assertEquals(false, metrics.taskAdherence.single().hasBoxPlot)
        assertEquals(0, metrics.taskBoxPlots.size)
    }

    @Test
    fun boxPlotUsesFirstTagAndSortsByMedianRatio() {
        val period = 1_000L
        val metrics = calculate(
            listOf(
                row(id = 1, taskId = 1, taskName = "Late", tags = "b,a", completionMillis = 10_000, previousMillis = null, periodMillis = period),
                row(id = 2, taskId = 1, taskName = "Late", tags = "b,a", completionMillis = 12_000, previousMillis = 10_000, periodMillis = period),
                row(id = 3, taskId = 1, taskName = "Late", tags = "b,a", completionMillis = 14_000, previousMillis = 12_000, periodMillis = period),
                row(id = 4, taskId = 1, taskName = "Late", tags = "b,a", completionMillis = 16_000, previousMillis = 14_000, periodMillis = period),
                row(id = 5, taskId = 1, taskName = "Late", tags = "b,a", completionMillis = 18_000, previousMillis = 16_000, periodMillis = period),
                row(id = 6, taskId = 2, taskName = "Early", tags = "z", completionMillis = 10_000, previousMillis = null, periodMillis = period),
                row(id = 7, taskId = 2, taskName = "Early", tags = "z", completionMillis = 10_500, previousMillis = 10_000, periodMillis = period),
                row(id = 8, taskId = 2, taskName = "Early", tags = "z", completionMillis = 11_000, previousMillis = 10_500, periodMillis = period),
                row(id = 9, taskId = 2, taskName = "Early", tags = "z", completionMillis = 11_500, previousMillis = 11_000, periodMillis = period),
                row(id = 10, taskId = 2, taskName = "Early", tags = "z", completionMillis = 12_000, previousMillis = 11_500, periodMillis = period)
            ),
            LocalDate.parse("1970-01-01"),
            LocalDate.parse("1970-01-02")
        )
        assertEquals(listOf("Early", "Late"), metrics.taskBoxPlots.map { it.taskName })
        assertEquals("b", metrics.taskBoxPlots.first { it.taskName == "Late" }.firstTag)
        assertEquals("z", metrics.taskBoxPlots.first { it.taskName == "Early" }.firstTag)
    }

    @Test
    fun perTaskAdherenceUsesAllTasksEvenWhenGroupedFromTaskTable() {
        val period = 86_400_000L
        val task = Task(id = 1, name = "Cadence", periodInMillis = period)
        val metrics = calculate(
            listOf(
                row(id = 1, taskId = 1, taskName = "Cadence", completionMillis = instant("2026-01-01")),
                row(id = 2, taskId = 1, taskName = "Cadence", completionMillis = instant("2026-01-02"))
            ),
            LocalDate.parse("2026-01-01"),
            LocalDate.parse("2026-01-03"),
            tasks = listOf(task)
        )

        assertEquals(1, metrics.taskAdherence.size)
        assertEquals("Cadence", metrics.taskAdherence.single().taskName)
    }

    @Test
    fun perTaskAdherenceWorksFromCompletionRowsWithoutTaskList() {
        val period = 86_400_000L
        val metrics = calculate(
            listOf(
                row(
                    id = 1,
                    taskId = 1,
                    taskName = "Cadence",
                    completionMillis = instant("2026-01-01"),
                    previousMillis = null,
                    periodMillis = period
                ),
                row(
                    id = 2,
                    taskId = 1,
                    taskName = "Cadence",
                    completionMillis = instant("2026-01-02"),
                    previousMillis = instant("2026-01-01"),
                    periodMillis = period
                )
            ),
            LocalDate.parse("2026-01-01"),
            LocalDate.parse("2026-01-03"),
            tasks = emptyList()
        )

        assertEquals(1, metrics.taskAdherence.size)
        assertEquals("Cadence", metrics.taskAdherence.single().taskName)
    }

    private fun calculate(
        rows: List<AnalyticsCompletionRow>,
        start: LocalDate?,
        end: LocalDate,
        tasks: List<Task> = emptyList(),
        binSize: AnalyticsBinSize? = null
    ): AnalyticsMetrics = AnalyticsCalculator.calculate(
        rows = rows,
        rangeStart = start,
        rangeEnd = end,
        zoneId = zone,
        tasks = tasks,
        requestedBinSize = binSize
    )

    private fun row(
        id: Int,
        taskId: Int = 1,
        taskName: String = "Task",
        isActive: Boolean = true,
        completionMillis: Long,
        previousMillis: Long? = null,
        periodMillis: Long = 1_000,
        tags: String = ""
    ) = AnalyticsCompletionRow(
        completionId = id,
        taskId = taskId,
        completionTime = Date(completionMillis),
        taskName = taskName,
        tags = tags,
        taskPeriodInMillis = periodMillis,
        isActive = isActive,
        previousCompletionTime = previousMillis?.let(::Date)
    )

    private fun instant(date: String, hour: Int = 12): Long =
        LocalDate.parse(date).atTime(hour, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
}
