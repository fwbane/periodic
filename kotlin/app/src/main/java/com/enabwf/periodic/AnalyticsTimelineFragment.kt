package com.enabwf.periodic

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.google.android.material.button.MaterialButtonToggleGroup
import com.patrykandpatrick.vico.views.cartesian.CartesianChartView

class AnalyticsTimelineFragment : AnalyticsTabFragment(R.layout.fragment_analytics_timeline) {
    private lateinit var contentViews: List<View>
    private lateinit var totalChart: CartesianChartView
    private lateinit var tagsChart: CartesianChartView
    private lateinit var binToggle: MaterialButtonToggleGroup
    private var rendering = false

    override fun onViewCreated(view: View, savedInstanceState: android.os.Bundle?) {
        totalChart = view.findViewById(R.id.timeline_total_chart)
        tagsChart = view.findViewById(R.id.timeline_tags_chart)
        binToggle = view.findViewById(R.id.bin_toggle)
        totalChart.modelProducer = viewModel.timelineTotalProducer
        tagsChart.modelProducer = viewModel.timelineTagsProducer
        contentViews = listOf(
            view.findViewById(R.id.bin_toggle),
            view.findViewById(R.id.timeline_total_title),
            view.findViewById(R.id.timeline_total_chart),
            view.findViewById(R.id.timeline_tags_title),
            view.findViewById(R.id.timeline_tags_chart),
            view.findViewById(R.id.timeline_tags_legend)
        )
        binToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked || rendering) return@addOnButtonCheckedListener
            val binSize = when (checkedId) {
                R.id.bin_hour -> AnalyticsBinSize.HOUR
                R.id.bin_day -> AnalyticsBinSize.DAY
                R.id.bin_week -> AnalyticsBinSize.WEEK
                R.id.bin_month -> AnalyticsBinSize.MONTH
                else -> return@addOnButtonCheckedListener
            }
            viewModel.setBinSize(binSize)
        }
        super.onViewCreated(view, savedInstanceState)
    }

    override fun render(state: AnalyticsUiState) {
        rendering = true
        val visible = state is AnalyticsUiState.Content
        contentViews.forEach { it.visibility = if (visible) View.VISIBLE else View.GONE }
        if (state !is AnalyticsUiState.Content) {
            rendering = false
            return
        }
        val binSize = state.filters.binSize ?: state.metrics.timeline.binSize
        val checkedId = when (binSize) {
            AnalyticsBinSize.HOUR -> R.id.bin_hour
            AnalyticsBinSize.DAY -> R.id.bin_day
            AnalyticsBinSize.WEEK -> R.id.bin_week
            AnalyticsBinSize.MONTH -> R.id.bin_month
        }
        binToggle.check(checkedId)
        val timeline = state.metrics.timeline
        val labels = AnalyticsLabelFormatter.timelineLabels(timeline)
        val binCount = timeline.bins.size
        AnalyticsChartConfigurator.configureTimelineChart(
            requireContext(),
            totalChart,
            labels,
            binCount
        )
        val tagNames = timeline.tagSeries.keys.toList()
        AnalyticsChartConfigurator.configureStackedColumnChart(
            requireContext(),
            tagsChart,
            labels,
            binCount,
            tagNames.size
        )
        view?.findViewById<ViewGroup>(R.id.timeline_tags_legend)?.let { legend ->
            val colors = AnalyticsChartColors.seriesColors(requireContext(), tagNames.size)
            AnalyticsChartLegend.bind(
                legend,
                tagNames.mapIndexed { index, tag ->
                    AnalyticsChartLegend.LegendEntry(
                        label = getString(
                            R.string.analytics_tag_legend_entry,
                            tag,
                            timeline.tagSeries.getValue(tag).sum()
                        ),
                        color = colors[index]
                    )
                }
            )
        }
        view?.findViewById<TextView>(R.id.timeline_tags_title)?.text =
            getString(R.string.analytics_timeline_by_tag)
        rendering = false
    }
}
