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
        val metrics = calculate(
            listOf(
                row(id = 1, completionMillis = instant("2026-01-01"), previousMillis = null, periodMillis = period),
                row(
                    id = 2,
                    completionMillis = instant("2026-01-02"),
                    previousMillis = instant("2026-01-01"),
                    periodMillis = period
                )
            ),
            LocalDate.parse("2026-01-01"),
            LocalDate.parse("2026-01-03"),
            tasks = listOf(task)
        )

        val adherence = metrics.taskAdherence.single()
        assertEquals(100.0, adherence.adherencePercent, 0.01)
        assertEquals(1, adherence.onScheduleCount)
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
