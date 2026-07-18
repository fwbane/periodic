package com.enabwf.periodic

import android.content.Context
import com.google.android.material.color.MaterialColors
import com.patrykandpatrick.vico.views.cartesian.CartesianChartView
import com.patrykandpatrick.vico.views.cartesian.Zoom
import com.patrykandpatrick.vico.views.cartesian.ZoomHandler
import com.patrykandpatrick.vico.views.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.views.cartesian.data.CandlestickCartesianLayerModel
import com.patrykandpatrick.vico.views.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.views.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.views.cartesian.data.ColumnCartesianLayerModel
import com.patrykandpatrick.vico.views.cartesian.decoration.HorizontalLine
import com.patrykandpatrick.vico.views.cartesian.layer.CandlestickCartesianLayer
import com.patrykandpatrick.vico.views.cartesian.layer.ColumnCartesianLayer
import com.patrykandpatrick.vico.views.cartesian.marker.CandlestickCartesianLayerMarkerTarget
import com.patrykandpatrick.vico.views.cartesian.marker.DefaultCartesianMarker
import com.patrykandpatrick.vico.views.common.Fill
import com.patrykandpatrick.vico.views.common.component.LineComponent
import com.patrykandpatrick.vico.views.common.component.TextComponent
import com.patrykandpatrick.vico.views.common.data.ExtraStore
import com.patrykandpatrick.vico.views.common.shape.CorneredShape
import java.util.concurrent.TimeUnit

object AnalyticsChartConfigurator {
    private const val MIN_SPACING_DP = 4f
    private const val STACKED_COLUMN_THICKNESS_DP = 16f
    private const val BOX_CANDLE_THICKNESS_DP = 14f

    fun configureCategoryChart(
        context: Context,
        chartView: CartesianChartView,
        labels: List<String>,
        showAxisLabels: Boolean = true
    ) {
        chartView.scrollHandler.scrollEnabled = false
        applyAxis(context, chartView, labels, labels.size, showAxisLabels)
    }

    fun configureColoredCategoryChart(
        context: Context,
        chartView: CartesianChartView,
        labels: List<String>,
        colors: List<Int>,
        showAxisLabels: Boolean = true
    ) {
        chartView.scrollHandler.scrollEnabled = false
        applyPerColumnColors(context, chartView, colors)
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
            applyContentFitZoom(chartView, zoomEnabled = false)
            chartView.scrollHandler.scrollEnabled = true
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
            if (fitToWidth) {
                applyContentFitZoom(chartView, zoomEnabled = true)
                chartView.scrollHandler.scrollEnabled = true
            } else {
                chartView.scrollHandler.scrollEnabled = true
            }
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
        configureTimeSeriesChart(context, chartView, labels, binCount, fitToWidth = true)
    }

    fun configureBoxPlotChart(
        context: Context,
        chartView: CartesianChartView,
        tasks: List<AnalyticsTaskAdherence>
    ) {
        chartView.post {
            applyContentFitZoom(chartView, zoomEnabled = true)
            chartView.scrollHandler.scrollEnabled = true
            applyBoxPlotLayer(context, chartView, tasks)
            val labels = tasks.map { truncateLabel(it.taskName) }
            applyAxis(
                context = context,
                chartView = chartView,
                labels = labels,
                binCount = tasks.size.coerceAtLeast(1),
                showAxisLabels = true,
                markerFormatter = boxPlotMarkerFormatter(context, tasks),
                markerLineCount = 3
            )
            chartView.invalidate()
        }
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

    fun formatDuration(milliseconds: Long): String {
        val days = TimeUnit.MILLISECONDS.toDays(milliseconds)
        if (days > 0) return "$days day${if (days == 1L) "" else "s"}"
        val hours = TimeUnit.MILLISECONDS.toHours(milliseconds)
        if (hours > 0) return "$hours hour${if (hours == 1L) "" else "s"}"
        val minutes = TimeUnit.MILLISECONDS.toMinutes(milliseconds).coerceAtLeast(1)
        return "$minutes minute${if (minutes == 1L) "" else "s"}"
    }

    private fun applyContentFitZoom(chartView: CartesianChartView, zoomEnabled: Boolean) {
        chartView.zoomHandler = ZoomHandler(
            zoomEnabled = zoomEnabled,
            initialZoom = Zoom.Content,
            minZoom = Zoom.Content
        )
    }

    private fun applyAxis(
        context: Context,
        chartView: CartesianChartView,
        labels: List<String>,
        binCount: Int,
        showAxisLabels: Boolean,
        markerFormatter: DefaultCartesianMarker.ValueFormatter =
            DefaultCartesianMarker.ValueFormatter.default(),
        markerLineCount: Int = 1
    ) {
        val marker = DefaultCartesianMarker(
            label = TextComponent(
                color = MaterialColors.getColor(
                    context,
                    com.google.android.material.R.attr.colorOnSurface,
                    0
                ),
                lineCount = markerLineCount,
                textAlignment = android.text.Layout.Alignment.ALIGN_CENTER
            ),
            valueFormatter = markerFormatter
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

    private fun applyPerColumnColors(
        context: Context,
        chartView: CartesianChartView,
        colors: List<Int>
    ) {
        val chartModel = chartView.chart ?: return
        val columnLayer = chartModel.layers.filterIsInstance<ColumnCartesianLayer>().firstOrNull()
            ?: return
        val fallback = AnalyticsChartColors.seriesColors(context, 1).first()
        val components = colors.ifEmpty { listOf(fallback) }.map { color ->
            LineComponent(
                fill = Fill(color),
                thicknessDp = STACKED_COLUMN_THICKNESS_DP,
                shape = CorneredShape.rounded(allPercent = 20)
            )
        }
        val coloredLayer = columnLayer.copy(
            columnProvider = object : ColumnCartesianLayer.ColumnProvider {
                override fun getColumn(
                    entry: ColumnCartesianLayerModel.Entry,
                    extraStore: ExtraStore
                ): LineComponent {
                    val index = entry.x.toInt().coerceIn(0, components.lastIndex)
                    return components[index]
                }

                override fun getWidestSeriesColumn(
                    seriesKey: Any,
                    seriesIndex: Int,
                    extraStore: ExtraStore
                ): LineComponent = components.maxBy { it.thicknessDp }
            }
        )
        chartView.chart = chartModel.copy(layers = arrayOf(coloredLayer))
    }

    private fun applyBoxPlotLayer(
        context: Context,
        chartView: CartesianChartView,
        tasks: List<AnalyticsTaskAdherence>
    ) {
        val chartModel = chartView.chart ?: return
        val candleLayer = chartModel.layers.filterIsInstance<CandlestickCartesianLayer>().firstOrNull()
            ?: return
        val tagColors = AnalyticsTagColors.colorMap(context, tasks.map { it.firstTag })
        val candlesByIndex = tasks.map { task ->
            candleForColor(tagColors.getValue(task.firstTag))
        }
        val fallback = candleForColor(
            AnalyticsChartColors.seriesColors(context, 1).first()
        )
        val provider = object : CandlestickCartesianLayer.CandleProvider {
            override fun getCandle(
                entry: CandlestickCartesianLayerModel.Entry,
                key: Any,
                extraStore: ExtraStore
            ): CandlestickCartesianLayer.Candle {
                val index = entry.x.toInt()
                return candlesByIndex.getOrElse(index) { fallback }
            }

            override fun getWidestCandle(
                key: Any,
                extraStore: ExtraStore
            ): CandlestickCartesianLayer.Candle =
                (candlesByIndex + fallback).maxBy { it.widthDp }
        }
        val referenceColor = MaterialColors.getColor(
            context,
            com.google.android.material.R.attr.colorOutline,
            0
        )
        val referenceLine = HorizontalLine(
            y = { _ -> 1.0 },
            line = LineComponent(
                fill = Fill(referenceColor),
                thicknessDp = 1.5f
            ),
            labelComponent = TextComponent(
                color = MaterialColors.getColor(
                    context,
                    com.google.android.material.R.attr.colorOnSurfaceVariant,
                    0
                )
            ),
            label = { _ -> "1.0" }
        )
        chartView.chart = chartModel.copy(
            layers = arrayOf(
                candleLayer.copy(
                    candleProvider = provider,
                    rangeProvider = CartesianLayerRangeProvider.fixed(
                        minY = 0.0,
                        maxY = AnalyticsCalculator.BOX_PLOT_MAX_RATIO
                    )
                )
            ),
            decorations = listOf(referenceLine)
        )
    }

    private fun candleForColor(color: Int): CandlestickCartesianLayer.Candle {
        val body = LineComponent(
            fill = Fill(color),
            thicknessDp = BOX_CANDLE_THICKNESS_DP
        )
        return CandlestickCartesianLayer.Candle(body = body)
    }

    private fun boxPlotMarkerFormatter(
        context: Context,
        tasks: List<AnalyticsTaskAdherence>
    ): DefaultCartesianMarker.ValueFormatter =
        DefaultCartesianMarker.ValueFormatter { _, targets ->
            val target = targets.filterIsInstance<CandlestickCartesianLayerMarkerTarget>().firstOrNull()
                ?: return@ValueFormatter ""
            val index = target.entry.x.toInt()
            val task = tasks.getOrNull(index) ?: return@ValueFormatter ""
            context.getString(
                com.enabwf.periodic.R.string.analytics_adherence_box_marker,
                task.taskName,
                formatDuration(task.periodInMillis),
                formatDuration(task.meanIntervalMillis),
                formatDuration(task.medianIntervalMillis)
            )
        }

    private fun truncateLabel(name: String, maxChars: Int = 10): String =
        if (name.length <= maxChars) name else name.take(maxChars - 1) + "…"

    private fun categoryFormatter(labels: List<String>): CartesianValueFormatter =
        CartesianValueFormatter { _, value, _ ->
            labels.getOrNull(value.toInt()) ?: value.toInt().toString()
        }
}
