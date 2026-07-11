package com.enabwf.periodic

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.tabs.TabLayoutMediator
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
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

    private lateinit var toolbar: MaterialToolbar
    private lateinit var rangeToggle: MaterialButtonToggleGroup
    private lateinit var rangeSummary: TextView
    private lateinit var progress: ProgressBar
    private lateinit var statusContainer: View
    private lateinit var statusMessage: TextView
    private lateinit var retryButton: MaterialButton
    private lateinit var pager: ViewPager2
    private lateinit var tabs: TabLayout
    private var taskOptions: List<AnalyticsTaskOption> = emptyList()
    private var rendering = false
    private var filterSheet: BottomSheetDialog? = null
    private var filterSheetView: View? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_analytics)

        toolbar = findViewById(R.id.analytics_toolbar)
        toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
        toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.action_analytics_filters) {
                showFiltersSheet()
                true
            } else {
                false
            }
        }

        rangeToggle = findViewById(R.id.range_toggle)
        rangeSummary = findViewById(R.id.range_summary)
        progress = findViewById(R.id.analytics_progress)
        statusContainer = findViewById(R.id.status_container)
        statusMessage = findViewById(R.id.status_message)
        retryButton = findViewById(R.id.retry_button)
        pager = findViewById(R.id.analytics_pager)
        tabs = findViewById(R.id.analytics_tabs)

        pager.adapter = AnalyticsPagerAdapter(this)
        TabLayoutMediator(tabs, pager) { tab, position ->
            tab.text = getString(
                when (position) {
                    0 -> R.string.analytics_tab_overview
                    1 -> R.string.analytics_tab_timeline
                    2 -> R.string.analytics_tab_adherence
                    else -> R.string.analytics_tab_patterns
                }
            )
        }.attach()

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
    }

    private fun showFiltersSheet() {
        val state = viewModel.uiState.value ?: return
        val sheetView = layoutInflater.inflate(R.layout.bottom_sheet_analytics_filters, null)
        val sheet = filterSheet ?: BottomSheetDialog(this).also { filterSheet = it }
        sheet.setContentView(sheetView)
        filterSheetView = sheetView

        val tagFilter = sheetView.findViewById<AutoCompleteTextView>(R.id.sheet_tag_filter)
        val taskFilter = sheetView.findViewById<AutoCompleteTextView>(R.id.sheet_task_filter)
        val includeArchived = sheetView.findViewById<MaterialSwitch>(R.id.sheet_include_archived)
        val sheetRangeSummary = sheetView.findViewById<TextView>(R.id.sheet_range_summary)

        val options = when (state) {
            is AnalyticsUiState.Content -> state.options
            is AnalyticsUiState.Empty -> state.options
            else -> AnalyticsFilterOptions()
        }
        bindSheetOptions(sheetView, options, state.filters)
        sheetRangeSummary.text = filterSummary(state.filters, options)

        sheetView.findViewById<MaterialButton>(R.id.sheet_custom_range_button)
            .setOnClickListener {
                sheet.dismiss()
                showDateRangePicker()
            }
        tagFilter.setOnItemClickListener { parent, _, position, _ ->
            val value = parent.getItemAtPosition(position).toString()
            viewModel.setExactTag(value.takeUnless { it == getString(R.string.analytics_all_tags) })
        }
        taskFilter.setOnItemClickListener { _, _, position, _ ->
            viewModel.setTaskFilter(
                if (position == 0) null else taskOptions.getOrNull(position - 1)?.taskId
            )
        }
        includeArchived.setOnCheckedChangeListener { _, checked ->
            viewModel.setIncludeArchived(checked)
        }

        sheet.show()
    }

    private fun bindSheetOptions(
        sheetView: View,
        options: AnalyticsFilterOptions,
        filters: AnalyticsFilterState
    ) {
        val tagFilter = sheetView.findViewById<AutoCompleteTextView>(R.id.sheet_tag_filter)
        val taskFilter = sheetView.findViewById<AutoCompleteTextView>(R.id.sheet_task_filter)
        val includeArchived = sheetView.findViewById<MaterialSwitch>(R.id.sheet_include_archived)

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
        includeArchived.isChecked = filters.includeArchived
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
        renderFilters(state)
        when (state) {
            is AnalyticsUiState.Loading -> showLoading()
            is AnalyticsUiState.Empty -> {
                showStatus(getString(R.string.analytics_no_data), retry = false)
            }
            is AnalyticsUiState.Error -> {
                showStatus(state.message.ifBlank { getString(R.string.analytics_error) }, retry = true)
            }
            is AnalyticsUiState.Content -> showContent()
        }
        rendering = false
    }

    private fun renderFilters(state: AnalyticsUiState) {
        val filters = state.filters
        val options = when (state) {
            is AnalyticsUiState.Content -> state.options
            is AnalyticsUiState.Empty -> state.options
            else -> AnalyticsFilterOptions()
        }
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
        rangeSummary.text = filterSummary(filters, options)
        filterSheetView?.let { bindSheetOptions(it, options, filters) }
    }

    private fun filterSummary(filters: AnalyticsFilterState, options: AnalyticsFilterOptions): String {
        val formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
        val range = filters.startDate?.let {
            getString(
                R.string.analytics_range_summary,
                formatter.format(it),
                formatter.format(filters.endDate)
            )
        } ?: getString(R.string.analytics_range_until, formatter.format(filters.endDate))
        val tag = filters.exactTag ?: getString(R.string.analytics_all_tags)
        val taskName = filters.taskId?.let { id ->
            options.tasks.firstOrNull { it.taskId == id }?.taskName
        } ?: getString(R.string.analytics_all_tasks)
        val archived = if (filters.includeArchived) {
            getString(R.string.analytics_include_archived)
        } else {
            getString(R.string.analytics_active_only)
        }
        return getString(R.string.analytics_filter_summary, range, tag, taskName, archived)
    }

    private fun showLoading() {
        progress.visibility = View.VISIBLE
        statusContainer.visibility = View.GONE
        pager.visibility = View.GONE
        tabs.visibility = View.VISIBLE
    }

    private fun showContent() {
        progress.visibility = View.GONE
        statusContainer.visibility = View.GONE
        pager.visibility = View.VISIBLE
        tabs.visibility = View.VISIBLE
    }

    private fun showStatus(message: String, retry: Boolean) {
        progress.visibility = View.GONE
        pager.visibility = View.GONE
        tabs.visibility = View.VISIBLE
        statusContainer.visibility = View.VISIBLE
        statusMessage.text = message
        retryButton.visibility = if (retry) View.VISIBLE else View.GONE
    }

    private fun Long.toUtcLocalDate(): LocalDate =
        Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
}
