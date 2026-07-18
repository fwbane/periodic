package com.enabwf.periodic

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.patrykandpatrick.vico.views.cartesian.CartesianChartView

class AnalyticsAdherenceFragment : AnalyticsTabFragment(R.layout.fragment_analytics_adherence) {
    private lateinit var contentViews: List<View>
    private lateinit var summaryChart: CartesianChartView
    private lateinit var adherenceList: ViewGroup

    override fun onViewCreated(view: View, savedInstanceState: android.os.Bundle?) {
        summaryChart = view.findViewById(R.id.adherence_summary_chart)
        adherenceList = view.findViewById(R.id.adherence_list)
        summaryChart.modelProducer = viewModel.adherenceChartProducer
        contentViews = listOf(
            view.findViewById(R.id.adherence_summary_title),
            view.findViewById(R.id.adherence_summary_chart),
            view.findViewById(R.id.adherence_box_legend),
            view.findViewById(R.id.adherence_list_title),
            view.findViewById(R.id.adherence_list)
        )
        super.onViewCreated(view, savedInstanceState)
    }

    override fun render(state: AnalyticsUiState) {
        val visible = state is AnalyticsUiState.Content
        contentViews.forEach { it.visibility = if (visible) View.VISIBLE else View.GONE }
        if (state !is AnalyticsUiState.Content) {
            view?.findViewById<TextView>(R.id.adherence_empty)?.visibility = View.GONE
            view?.findViewById<TextView>(R.id.adherence_box_empty)?.visibility = View.GONE
            return
        }

        val rows = state.metrics.taskAdherence
        val boxPlots = state.metrics.taskBoxPlots
        AnalyticsAdherenceRowBinder.bind(adherenceList, rows)
        adherenceList.requestLayout()
        view?.findViewById<TextView>(R.id.adherence_empty)?.apply {
            visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
        }
        view?.findViewById<TextView>(R.id.adherence_box_empty)?.apply {
            visibility = if (boxPlots.isEmpty()) View.VISIBLE else View.GONE
        }
        summaryChart.visibility = if (boxPlots.isEmpty()) View.GONE else View.VISIBLE
        if (boxPlots.isNotEmpty()) {
            AnalyticsChartConfigurator.configureBoxPlotChart(
                requireContext(),
                summaryChart,
                boxPlots
            )
        }
        view?.findViewById<ViewGroup>(R.id.adherence_box_legend)?.let { legend ->
            val tags = boxPlots.map { it.firstTag }.distinct()
            val colors = AnalyticsTagColors.colorMap(requireContext(), tags)
            AnalyticsChartLegend.bind(
                legend,
                tags.map { tag ->
                    AnalyticsChartLegend.LegendEntry(
                        label = tag,
                        color = colors.getValue(tag)
                    )
                }
            )
        }
    }
}
