package com.enabwf.periodic

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.patrykandpatrick.vico.views.cartesian.CartesianChartView
import java.util.Locale

class AnalyticsOverviewFragment : AnalyticsTabFragment(R.layout.fragment_analytics_overview) {
    private lateinit var contentViews: List<View>
    private lateinit var trendChart: CartesianChartView
    private lateinit var topTasksChart: CartesianChartView
    private lateinit var taskAdapter: AnalyticsTaskAdapter

    override fun onViewCreated(view: View, savedInstanceState: android.os.Bundle?) {
        trendChart = view.findViewById(R.id.trend_chart)
        topTasksChart = view.findViewById(R.id.top_tasks_chart)
        trendChart.modelProducer = viewModel.trendChartProducer
        topTasksChart.modelProducer = viewModel.overviewTopTasksProducer
        contentViews = listOf(
            view.findViewById(R.id.summary_cards),
            view.findViewById(R.id.top_tasks_title),
            view.findViewById(R.id.top_tasks_chart),
            view.findViewById(R.id.top_tasks_legend),
            view.findViewById(R.id.trend_title),
            view.findViewById(R.id.trend_chart),
            view.findViewById(R.id.tasks_title),
            view.findViewById(R.id.task_rankings),
            view.findViewById(R.id.tags_title),
            view.findViewById(R.id.tag_rankings)
        )
        taskAdapter = AnalyticsTaskAdapter { ranking -> viewModel.setTaskFilter(ranking.taskId) }
        view.findViewById<RecyclerView>(R.id.task_rankings).apply {
            adapter = taskAdapter
            layoutManager = LinearLayoutManager(requireContext())
            isNestedScrollingEnabled = true
        }
        super.onViewCreated(view, savedInstanceState)
    }

    override fun render(state: AnalyticsUiState) {
        val visible = state is AnalyticsUiState.Content
        contentViews.forEach { it.visibility = if (visible) View.VISIBLE else View.GONE }
        if (state !is AnalyticsUiState.Content) return
        val metrics = state.metrics
        view?.findViewById<TextView>(R.id.completion_value)?.text =
            metrics.summary.completionCount.toString()
        view?.findViewById<TextView>(R.id.active_tasks_value)?.text =
            metrics.overview.activeTaskCount.toString()
        view?.findViewById<TextView>(R.id.avg_per_day_value)?.text =
            String.format(Locale.getDefault(), "%.1f", metrics.overview.averageCompletionsPerDay)
        taskAdapter.submitList(metrics.taskRankings)
        view?.findViewById<TextView>(R.id.tag_rankings)?.text =
            metrics.tagRankings.joinToString("\n") {
                getString(R.string.analytics_tag_count, it.tag, it.completionCount)
            }
        val trendLabels = AnalyticsLabelFormatter.trendLabels(metrics)
        AnalyticsChartConfigurator.configureTimeSeriesChart(
            requireContext(),
            trendChart,
            trendLabels,
            metrics.trend.size
        )
        val topTasks = metrics.overview.topTasks
        val tagColors = AnalyticsTagColors.colorMap(
            requireContext(),
            topTasks.map { it.firstTag }
        )
        val barColors = topTasks.map { tagColors.getValue(it.firstTag) }
        AnalyticsChartConfigurator.configureColoredCategoryChart(
            requireContext(),
            topTasksChart,
            AnalyticsLabelFormatter.topTaskLabels(topTasks),
            barColors,
            showAxisLabels = false
        )
        view?.findViewById<ViewGroup>(R.id.top_tasks_legend)?.let { legend ->
            AnalyticsChartLegend.bind(
                legend,
                topTasks.map { task ->
                    AnalyticsChartLegend.LegendEntry(
                        label = getString(
                            R.string.analytics_top_task_legend_entry,
                            task.taskName,
                            task.completionCount
                        ),
                        color = tagColors.getValue(task.firstTag)
                    )
                }
            )
        }
    }
}
