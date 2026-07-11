package com.enabwf.periodic

import android.view.View
import com.patrykandpatrick.vico.views.cartesian.CartesianChartView

class AnalyticsPatternsFragment : AnalyticsTabFragment(R.layout.fragment_analytics_patterns) {
    private lateinit var contentViews: List<View>
    private lateinit var hourlyChart: CartesianChartView
    private lateinit var dailyChart: CartesianChartView
    private lateinit var heatmap: HeatmapView

    override fun onViewCreated(view: View, savedInstanceState: android.os.Bundle?) {
        hourlyChart = view.findViewById(R.id.hourly_chart)
        dailyChart = view.findViewById(R.id.daily_chart)
        heatmap = view.findViewById(R.id.heatmap)
        hourlyChart.modelProducer = viewModel.patternsHourlyProducer
        dailyChart.modelProducer = viewModel.patternsDailyProducer
        contentViews = listOf(
            view.findViewById(R.id.hourly_title),
            view.findViewById(R.id.hourly_chart),
            view.findViewById(R.id.daily_title),
            view.findViewById(R.id.daily_chart),
            view.findViewById(R.id.heatmap_title),
            view.findViewById(R.id.heatmap)
        )
        super.onViewCreated(view, savedInstanceState)
    }

    override fun render(state: AnalyticsUiState) {
        val visible = state is AnalyticsUiState.Content
        contentViews.forEach { it.visibility = if (visible) View.VISIBLE else View.GONE }
        if (state !is AnalyticsUiState.Content) return
        AnalyticsChartConfigurator.configureTimeSeriesChart(
            requireContext(),
            hourlyChart,
            AnalyticsLabelFormatter.hourlyLabels(),
            binCount = 24,
            fitToWidth = false
        )
        AnalyticsChartConfigurator.configureCategoryChart(
            requireContext(),
            dailyChart,
            AnalyticsLabelFormatter.dailyLabels()
        )
        heatmap.setCells(state.metrics.patterns.heatmap)
    }
}
