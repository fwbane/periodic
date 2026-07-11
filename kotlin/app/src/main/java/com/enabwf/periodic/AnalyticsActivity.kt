package com.enabwf.periodic

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.appbar.MaterialToolbar
import com.patrykandpatrick.vico.views.cartesian.CartesianChartView
import com.patrykandpatrick.vico.views.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.views.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.views.cartesian.marker.DefaultCartesianMarker
import com.patrykandpatrick.vico.views.common.component.TextComponent
import com.google.android.material.color.MaterialColors
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

class AnalyticsActivity : AppCompatActivity() {
    private val viewModel: AnalyticsViewModel by viewModels {
        val database = TaskDatabase.getDatabase(application)
        AnalyticsViewModelFactory(TaskRepository(database.taskDao()))
    }

    private lateinit var rangeToggle: MaterialButtonToggleGroup
    private lateinit var customRangeButton: MaterialButton
    private lateinit var tagFilter: AutoCompleteTextView
    private lateinit var taskFilter: AutoCompleteTextView
    private lateinit var includeArchived: MaterialSwitch
    private lateinit var progress: ProgressBar
    private lateinit var statusContainer: View
    private lateinit var statusMessage: TextView
    private lateinit var retryButton: MaterialButton
    private lateinit var summaryViews: List<View>
    private lateinit var taskAdapter: AnalyticsTaskAdapter
    private lateinit var trendChart: CartesianChartView
    private lateinit var adherenceChart: CartesianChartView
    private var taskOptions: List<AnalyticsTaskOption> = emptyList()
    private var rendering = false
    private var chartsConfigured = false
    private var trendAxisLabels: List<String> = emptyList()
    private val adherenceAxisLabels = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_analytics)

        findViewById<MaterialToolbar>(R.id.analytics_toolbar)
            .setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        rangeToggle = findViewById(R.id.range_toggle)
        customRangeButton = findViewById(R.id.custom_range_button)
        tagFilter = findViewById(R.id.tag_filter)
        taskFilter = findViewById(R.id.task_filter)
        includeArchived = findViewById(R.id.include_archived)
        progress = findViewById(R.id.analytics_progress)
        statusContainer = findViewById(R.id.status_container)
        statusMessage = findViewById(R.id.status_message)
        retryButton = findViewById(R.id.retry_button)
        trendChart = findViewById(R.id.trend_chart)
        adherenceChart = findViewById(R.id.adherence_chart)
        summaryViews = listOf(
            findViewById(R.id.summary_cards),
            findViewById(R.id.trend_title),
            findViewById(R.id.adherence_title),
            findViewById(R.id.tasks_title),
            findViewById(R.id.task_rankings),
            findViewById(R.id.tags_title),
            findViewById(R.id.tag_rankings)
        )

        trendChart.modelProducer = viewModel.trendChartProducer
        adherenceChart.modelProducer = viewModel.adherenceChartProducer
        adherenceAxisLabels += listOf(
            getString(R.string.analytics_early),
            getString(R.string.analytics_on_schedule),
            getString(R.string.analytics_late)
        )
        trendChart.post { ensureChartsConfigured() }

        taskAdapter = AnalyticsTaskAdapter { ranking ->
            viewModel.setTaskFilter(ranking.taskId)
        }
        findViewById<RecyclerView>(R.id.task_rankings).apply {
            adapter = taskAdapter
            layoutManager = LinearLayoutManager(this@AnalyticsActivity)
            isNestedScrollingEnabled = false
        }

        bindFilterListeners()
        retryButton.setOnClickListener { viewModel.refresh() }
        viewModel.uiState.observe(this, ::render)
    }

    private fun bindFilterListeners() {
        rangeToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked || rendering) return@addOnButtonCheckedListener
            val preset = when (checkedId) {
                R.id.range_30_days -> AnalyticsRangePreset.LAST_30_DAYS
                R.id.range_90_days -> AnalyticsRangePreset.LAST_90_DAYS
                R.id.range_year -> AnalyticsRangePreset.LAST_YEAR
                R.id.range_all -> AnalyticsRangePreset.ALL_TIME
                else -> return@addOnButtonCheckedListener
            }
            viewModel.setRangePreset(preset)
        }
        customRangeButton.setOnClickListener { showDateRangePicker() }
        includeArchived.setOnCheckedChangeListener { _, checked ->
            if (!rendering) viewModel.setIncludeArchived(checked)
        }
        tagFilter.setOnItemClickListener { parent, _, position, _ ->
            if (!rendering) {
                val value = parent.getItemAtPosition(position).toString()
                viewModel.setExactTag(value.takeUnless { it == getString(R.string.analytics_all_tags) })
            }
        }
        taskFilter.setOnItemClickListener { _, _, position, _ ->
            if (!rendering) {
                viewModel.setTaskFilter(
                    if (position == 0) null else taskOptions.getOrNull(position - 1)?.taskId
                )
            }
        }
    }

    private fun showDateRangePicker() {
        val current = viewModel.uiState.value?.filters ?: return
        val builder = MaterialDatePicker.Builder.dateRangePicker()
            .setTitleText(R.string.analytics_select_range)
            .setCalendarConstraints(
                CalendarConstraints.Builder().setEnd(MaterialDatePicker.todayInUtcMilliseconds())
                    .build()
            )
        current.startDate?.let { start ->
            builder.setSelection(
                androidx.core.util.Pair(
                    start.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
                    current.endDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
                )
            )
        }
        val picker = builder.build()
        picker.addOnPositiveButtonClickListener { range ->
            viewModel.setCustomRange(range.first.toUtcLocalDate(), range.second.toUtcLocalDate())
        }
        val pickerTag = "ANALYTICS_DATE_RANGE"
        if (supportFragmentManager.findFragmentByTag(pickerTag) != null) return
        picker.show(supportFragmentManager, pickerTag)
    }

    private fun render(state: AnalyticsUiState) {
        rendering = true
        renderFilters(state.filters)
        when (state) {
            is AnalyticsUiState.Loading -> showLoading()
            is AnalyticsUiState.Empty -> {
                renderOptions(state.options, state.filters)
                showStatus(getString(R.string.analytics_no_data), retry = false)
            }
            is AnalyticsUiState.Error -> {
                showStatus(state.message.ifBlank { getString(R.string.analytics_error) }, retry = true)
            }
            is AnalyticsUiState.Content -> {
                renderOptions(state.options, state.filters)
                renderMetrics(state.metrics)
            }
        }
        rendering = false
    }

    private fun renderFilters(filters: AnalyticsFilterState) {
        val checkedId = when (filters.rangePreset) {
            AnalyticsRangePreset.LAST_30_DAYS -> R.id.range_30_days
            AnalyticsRangePreset.LAST_90_DAYS -> R.id.range_90_days
            AnalyticsRangePreset.LAST_YEAR -> R.id.range_year
            AnalyticsRangePreset.ALL_TIME -> R.id.range_all
            AnalyticsRangePreset.CUSTOM -> View.NO_ID
        }
        if (filters.rangePreset != AnalyticsRangePreset.CUSTOM) {
            rangeToggle.check(checkedId)
        }
        includeArchived.isChecked = filters.includeArchived
        val formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
        findViewById<TextView>(R.id.range_summary).text = filters.startDate?.let {
            getString(
                R.string.analytics_range_summary,
                formatter.format(it),
                formatter.format(filters.endDate)
            )
        } ?: getString(R.string.analytics_range_until, formatter.format(filters.endDate))
    }

    private fun renderOptions(options: AnalyticsFilterOptions, filters: AnalyticsFilterState) {
        val tagLabels = listOf(getString(R.string.analytics_all_tags)) + options.tags
        tagFilter.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, tagLabels)
        )
        tagFilter.setText(filters.exactTag ?: tagLabels.first(), false)

        taskOptions = options.tasks
        val taskLabels = listOf(getString(R.string.analytics_all_tasks)) +
            options.tasks.map { option ->
                if (option.isActive) option.taskName
                else "${option.taskName} (${getString(R.string.analytics_archived)})"
            }
        taskFilter.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, taskLabels)
        )
        val selectedTask = options.tasks.indexOfFirst { it.taskId == filters.taskId }
        taskFilter.setText(taskLabels.getOrElse(selectedTask + 1) { taskLabels.first() }, false)
    }

    private fun showLoading() {
        progress.visibility = View.VISIBLE
        statusContainer.visibility = View.GONE
        summaryViews.forEach { it.visibility = View.GONE }
        trendChart.visibility = View.VISIBLE
        adherenceChart.visibility = View.VISIBLE
    }

    private fun showStatus(message: String, retry: Boolean) {
        progress.visibility = View.GONE
        summaryViews.forEach { it.visibility = View.GONE }
        trendChart.visibility = View.VISIBLE
        adherenceChart.visibility = View.VISIBLE
        statusContainer.visibility = View.VISIBLE
        statusMessage.text = message
        retryButton.visibility = if (retry) View.VISIBLE else View.GONE
        taskAdapter.submitList(emptyList())
    }

    private fun renderMetrics(metrics: AnalyticsMetrics) {
        progress.visibility = View.GONE
        statusContainer.visibility = View.GONE
        summaryViews.forEach { it.visibility = View.VISIBLE }
        trendChart.visibility = View.VISIBLE
        adherenceChart.visibility = View.VISIBLE
        findViewById<TextView>(R.id.completion_value).text =
            metrics.summary.completionCount.toString()
        findViewById<TextView>(R.id.interval_value).text =
            metrics.summary.eligibleIntervalCount.toString()
        findViewById<TextView>(R.id.on_schedule_value).text =
            metrics.summary.onScheduleRate?.let {
                getString(R.string.analytics_rate_percent, it * 100)
            } ?: getString(R.string.analytics_rate_unavailable)
        taskAdapter.submitList(metrics.taskRankings)
        findViewById<TextView>(R.id.tag_rankings).text =
            metrics.tagRankings.joinToString("\n") {
                getString(R.string.analytics_tag_count, it.tag, it.completionCount)
            }

        trendAxisLabels = metrics.trend.map { point ->
            when (metrics.trendGranularity) {
                AnalyticsTrendGranularity.DAILY ->
                    point.periodStart.format(DateTimeFormatter.ofPattern("MMM d"))
                AnalyticsTrendGranularity.WEEKLY ->
                    point.periodStart.format(DateTimeFormatter.ofPattern("MMM d"))
                AnalyticsTrendGranularity.MONTHLY ->
                    point.periodStart.format(DateTimeFormatter.ofPattern("MMM yy"))
            }
        }
        trendChart.post { ensureChartsConfigured() }
    }

    private fun trendLabelSpacing(): Int {
        val count = trendAxisLabels.size
        return when {
            count <= 7 -> 1
            count <= 30 -> 4
            else -> maxOf(1, count / 6)
        }
    }

    private fun ensureChartsConfigured() {
        val marker = DefaultCartesianMarker(
            label = TextComponent(
                color = MaterialColors.getColor(
                    this,
                    com.google.android.material.R.attr.colorOnSurface,
                    0
                )
            ),
            valueFormatter = DefaultCartesianMarker.ValueFormatter.default()
        )
        if (!chartsConfigured) {
            val trendChartModel = trendChart.chart ?: return
            val trendBottomAxis = trendChartModel.bottomAxis as? HorizontalAxis ?: return
            trendChart.chart = trendChartModel.copy(
                bottomAxis = trendBottomAxis.copy(
                    itemPlacer = HorizontalAxis.ItemPlacer.aligned(
                        spacing = { _ -> trendLabelSpacing() }
                    ),
                    valueFormatter = CartesianValueFormatter { _, value, _ ->
                        trendAxisLabels.getOrNull(value.toInt()) ?: value.toInt().toString()
                    }
                ),
                marker = marker
            )
            val adherenceChartModel = adherenceChart.chart ?: return
            val adherenceBottomAxis = adherenceChartModel.bottomAxis as? HorizontalAxis ?: return
            adherenceChart.chart = adherenceChartModel.copy(
                bottomAxis = adherenceBottomAxis.copy(
                    itemPlacer = HorizontalAxis.ItemPlacer.aligned(spacing = { _ -> 1 }),
                    valueFormatter = CartesianValueFormatter { _, value, _ ->
                        adherenceAxisLabels.getOrNull(value.toInt()) ?: value.toInt().toString()
                    }
                ),
                marker = marker
            )
            chartsConfigured = true
        }
        trendChart.invalidate()
        adherenceChart.invalidate()
    }

    private fun Long.toUtcLocalDate(): LocalDate =
        Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
}
