package com.enabwf.periodic

import android.content.Context
import com.google.android.material.color.MaterialColors
import com.patrykandpatrick.vico.views.cartesian.CartesianChartView
import com.patrykandpatrick.vico.views.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.views.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.views.cartesian.layer.ColumnCartesianLayer
import com.patrykandpatrick.vico.views.cartesian.marker.DefaultCartesianMarker
import com.patrykandpatrick.vico.views.common.Fill
import com.patrykandpatrick.vico.views.common.component.LineComponent
import com.patrykandpatrick.vico.views.common.component.TextComponent
import com.patrykandpatrick.vico.views.common.shape.CorneredShape

object AnalyticsChartConfigurator {
    private const val MIN_SPACING_DP = 4f
    private const val STACKED_COLUMN_THICKNESS_DP = 16f

    fun configureCategoryChart(
        context: Context,
        chartView: CartesianChartView,
        labels: List<String>,
        showAxisLabels: Boolean = true
    ) {
        chartView.scrollHandler.scrollEnabled = false
        applyAxis(context, chartView, labels, labels.size, showAxisLabels)
    }

    fun configureStackedColumnChart(
        context: Context,
        chartView: CartesianChartView,
        labels: List<String>,
        binCount: Int,
        seriesCount: Int
    ) {
        chartView.post {
            val allowScroll = binCount > maxFitBins(chartView.width, chartView.resources.displayMetrics.density)
            chartView.scrollHandler.scrollEnabled = allowScroll
            applyStackedColumns(context, chartView, seriesCount)
            applyAxis(context, chartView, labels, binCount, showAxisLabels = true)
            chartView.invalidate()
        }
    }

    fun configureTimeSeriesChart(
        context: Context,
        chartView: CartesianChartView,
        labels: List<String>,
        binCount: Int,
        fitToWidth: Boolean = true
    ) {
        chartView.post {
            val density = chartView.resources.displayMetrics.density
            val maxFitBins = maxFitBins(chartView.width, density)
            val allowScroll = !fitToWidth || binCount > maxFitBins
            chartView.scrollHandler.scrollEnabled = allowScroll
            applyAxis(context, chartView, labels, binCount, showAxisLabels = true)
            chartView.invalidate()
        }
    }

    fun configureTimelineChart(
        context: Context,
        chartView: CartesianChartView,
        labels: List<String>,
        binCount: Int
    ) {
        configureTimeSeriesChart(context, chartView, labels, binCount, fitToWidth = false)
    }

    fun labelSpacing(binCount: Int): Int = when {
        binCount <= 7 -> 1
        binCount <= 30 -> maxOf(1, binCount / 7)
        else -> maxOf(1, binCount / 6)
    }

    fun maxFitBins(widthPx: Int, density: Float): Int {
        if (widthPx <= 0) return 30
        return (widthPx / density / MIN_SPACING_DP).toInt().coerceAtLeast(1)
    }

    fun computeSpacingDp(widthPx: Int, binCount: Int, density: Float): Float {
        if (widthPx <= 0 || binCount <= 0) return 8f
        val spacingPx = widthPx.toFloat() / binCount
        return (spacingPx / density).coerceIn(MIN_SPACING_DP, 32f)
    }

    private fun applyAxis(
        context: Context,
        chartView: CartesianChartView,
        labels: List<String>,
        binCount: Int,
        showAxisLabels: Boolean
    ) {
        val marker = DefaultCartesianMarker(
            label = TextComponent(
                color = MaterialColors.getColor(
                    context,
                    com.google.android.material.R.attr.colorOnSurface,
                    0
                )
            ),
            valueFormatter = DefaultCartesianMarker.ValueFormatter.default()
        )
        val chartModel = chartView.chart ?: return
        val bottomAxis = chartModel.bottomAxis as? HorizontalAxis ?: return
        chartView.chart = chartModel.copy(
            bottomAxis = if (showAxisLabels) {
                bottomAxis.copy(
                    itemPlacer = HorizontalAxis.ItemPlacer.aligned(
                        spacing = { _ -> labelSpacing(binCount) }
                    ),
                    valueFormatter = categoryFormatter(labels)
                )
            } else {
                // Vico rejects blank formatter strings; hide labels via null components instead.
                bottomAxis.copy(
                    label = null,
                    tick = null,
                    line = null,
                    itemPlacer = HorizontalAxis.ItemPlacer.aligned(spacing = { _ -> Int.MAX_VALUE }),
                    valueFormatter = categoryFormatter(labels)
                )
            },
            marker = marker
        )
        chartView.invalidate()
    }

    private fun applyStackedColumns(context: Context, chartView: CartesianChartView, seriesCount: Int) {
        val chartModel = chartView.chart ?: return
        val columnLayer = chartModel.layers.filterIsInstance<ColumnCartesianLayer>().firstOrNull()
            ?: return
        val colors = AnalyticsChartColors.seriesColors(context, seriesCount.coerceAtLeast(1))
        val columns = colors.map { color ->
            LineComponent(
                fill = Fill(color),
                thicknessDp = STACKED_COLUMN_THICKNESS_DP,
                shape = CorneredShape.rounded(allPercent = 20)
            )
        }
        val stackedLayer = columnLayer.copy(
            columnProvider = ColumnCartesianLayer.ColumnProvider.series(columns),
            mergeMode = { ColumnCartesianLayer.MergeMode.Stacked }
        )
        chartView.chart = chartModel.copy(layers = arrayOf(stackedLayer))
    }

    private fun categoryFormatter(labels: List<String>): CartesianValueFormatter =
        CartesianValueFormatter { _, value, _ ->
            labels.getOrNull(value.toInt()) ?: value.toInt().toString()
        }
}
