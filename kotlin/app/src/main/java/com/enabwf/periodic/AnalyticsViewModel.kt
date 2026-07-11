package com.enabwf.periodic

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import com.patrykandpatrick.vico.views.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.views.cartesian.data.columnSeries
import com.patrykandpatrick.vico.views.cartesian.data.lineSeries

class AnalyticsViewModel(
    private val repository: TaskRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val zoneId: ZoneId = clock.zone
) : ViewModel() {
    private var filters = presetFilters(AnalyticsRangePreset.LAST_90_DAYS)
    private var loadJob: Job? = null
    private var loadGeneration = 0L

    private val adherenceHistoryEndExclusive: Date = Date.from(
        LocalDate.of(3000, 1, 1).atStartOfDay(zoneId).toInstant()
    )

    val trendChartProducer = CartesianChartModelProducer()
    val adherenceChartProducer = CartesianChartModelProducer()
    val overviewTopTasksProducer = CartesianChartModelProducer()
    val timelineTotalProducer = CartesianChartModelProducer()
    val timelineTagsProducer = CartesianChartModelProducer()
    val patternsHourlyProducer = CartesianChartModelProducer()
    val patternsDailyProducer = CartesianChartModelProducer()

    private val _uiState = MutableLiveData<AnalyticsUiState>(
        AnalyticsUiState.Loading(filters)
    )
    val uiState: LiveData<AnalyticsUiState> = _uiState

    init {
        reload()
    }

    fun setRangePreset(preset: AnalyticsRangePreset) {
        require(preset != AnalyticsRangePreset.CUSTOM) {
            "Use setCustomRange for a custom analytics range."
        }
        updateFilters(presetFilters(preset).copy(
            exactTag = filters.exactTag,
            taskId = filters.taskId,
            includeArchived = filters.includeArchived,
            binSize = filters.binSize
        ))
    }

    fun setCustomRange(startDate: LocalDate, endDate: LocalDate) {
        require(!startDate.isAfter(endDate)) {
            "Analytics range start must not be after its end."
        }
        updateFilters(
            filters.copy(
                rangePreset = AnalyticsRangePreset.CUSTOM,
                startDate = startDate,
                endDate = endDate
            )
        )
    }

    fun setExactTag(tag: String?) {
        updateFilters(filters.copy(exactTag = tag?.trim()?.takeIf(String::isNotEmpty)))
    }

    fun setTaskFilter(taskId: Int?) {
        updateFilters(filters.copy(taskId = taskId))
    }

    fun setIncludeArchived(includeArchived: Boolean) {
        updateFilters(filters.copy(includeArchived = includeArchived))
    }

    fun setBinSize(binSize: AnalyticsBinSize) {
        updateFilters(filters.copy(binSize = binSize))
    }

    fun refresh() {
        reload()
    }

    private fun presetFilters(preset: AnalyticsRangePreset): AnalyticsFilterState {
        val today = LocalDate.now(clock)
        val startDate = when (preset) {
            AnalyticsRangePreset.LAST_30_DAYS -> today.minusDays(29)
            AnalyticsRangePreset.LAST_90_DAYS -> today.minusDays(89)
            AnalyticsRangePreset.LAST_YEAR -> today.minusDays(364)
            AnalyticsRangePreset.ALL_TIME -> null
            AnalyticsRangePreset.CUSTOM ->
                throw IllegalArgumentException("Custom ranges require explicit dates.")
        }
        return AnalyticsFilterState(
            rangePreset = preset,
            startDate = startDate,
            endDate = today
        )
    }

    private fun updateFilters(updated: AnalyticsFilterState) {
        if (updated == filters) return
        filters = updated
        reload()
    }

    private fun reload() {
        loadJob?.cancel()
        val generation = ++loadGeneration
        val requestedFilters = filters
        _uiState.value = AnalyticsUiState.Loading(requestedFilters)

        loadJob = viewModelScope.launch {
            try {
                val tasks = repository.getAnalyticsTasks(requestedFilters.includeArchived)
                val normalizedFilters = if (
                    requestedFilters.taskId != null &&
                    tasks.none { it.id == requestedFilters.taskId }
                ) {
                    requestedFilters.copy(taskId = null)
                } else {
                    requestedFilters
                }
                if (generation != loadGeneration) return@launch
                filters = normalizedFilters

                val options = AnalyticsFilterOptions(
                    tags = repository.getAllTags(),
                    tasks = tasks.map { task ->
                        AnalyticsTaskOption(
                            taskId = task.id,
                            taskName = task.name,
                            isActive = task.isActive
                        )
                    }
                )
                val startTime = normalizedFilters.startDate?.let { date ->
                    Date.from(date.atStartOfDay(zoneId).toInstant())
                }
                val endTimeExclusive = Date.from(
                    normalizedFilters.endDate.plusDays(1).atStartOfDay(zoneId).toInstant()
                )
                val rows = repository.getAnalyticsCompletions(
                    startTime = startTime,
                    endTimeExclusive = endTimeExclusive,
                    includeArchived = normalizedFilters.includeArchived,
                    taskId = normalizedFilters.taskId,
                    exactTag = normalizedFilters.exactTag
                )
                val adherenceRows = repository.getAnalyticsCompletions(
                    startTime = null,
                    endTimeExclusive = adherenceHistoryEndExclusive,
                    includeArchived = normalizedFilters.includeArchived,
                    taskId = normalizedFilters.taskId,
                    exactTag = normalizedFilters.exactTag
                )
                val metrics = AnalyticsCalculator.calculate(
                    rows = rows,
                    rangeStart = normalizedFilters.startDate,
                    rangeEnd = normalizedFilters.endDate,
                    zoneId = zoneId,
                    tasks = tasks,
                    requestedBinSize = normalizedFilters.binSize,
                    adherenceRows = adherenceRows
                )
                if (generation != loadGeneration) return@launch
                if (metrics.summary.completionCount > 0) {
                    updateChartModels(metrics)
                }
                if (generation != loadGeneration) return@launch
                _uiState.value = if (metrics.summary.completionCount == 0) {
                    AnalyticsUiState.Empty(normalizedFilters, options)
                } else {
                    AnalyticsUiState.Content(normalizedFilters, options, metrics)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (generation == loadGeneration) {
                    _uiState.value = AnalyticsUiState.Error(
                        filters = requestedFilters,
                        message = error.message ?: "Unable to load analytics."
                    )
                }
            }
        }
    }

    private suspend fun updateChartModels(metrics: AnalyticsMetrics) {
        trendChartProducer.runTransaction {
            lineSeries {
                series(
                    x = metrics.trend.indices.toList(),
                    y = metrics.trend.map { it.completionCount }
                )
            }
        }
        adherenceChartProducer.runTransaction {
            columnSeries {
                series(metrics.adherence.map { it.count })
            }
        }
        overviewTopTasksProducer.runTransaction {
            columnSeries {
                series(metrics.overview.topTasks.map { it.completionCount })
            }
        }
        timelineTotalProducer.runTransaction {
            columnSeries {
                series(metrics.timeline.bins.map { it.completionCount })
            }
        }
        timelineTagsProducer.runTransaction {
            columnSeries {
                metrics.timeline.tagSeries.values.forEach { values -> series(values) }
            }
        }
        patternsHourlyProducer.runTransaction {
            columnSeries {
                series(metrics.patterns.hourly.map { it.completionCount })
            }
        }
        patternsDailyProducer.runTransaction {
            columnSeries {
                series(metrics.patterns.daily.map { it.completionCount })
            }
        }
    }
}

class AnalyticsViewModelFactory(
    private val repository: TaskRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val zoneId: ZoneId = clock.zone
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AnalyticsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AnalyticsViewModel(repository, clock, zoneId) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
