package com.enabwf.periodic

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import com.google.android.material.color.MaterialColors
import kotlin.math.max

/**
 * Lightweight histogram for completion-interval ratios (interval / set period).
 * Bucket width is typically 0.05 (5% of the set period). Axis labels show real durations.
 */
class IntervalHistogramView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            1f,
            resources.displayMetrics
        )
        style = Paint.Style.STROKE
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            10f,
            resources.displayMetrics
        )
    }

    private var buckets: List<AnalyticsHistogramBucket> = emptyList()
    private var periodInMillis: Long = 0L
    private var maxCount: Int = 0

    private val barColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimary, 0)
    private val axisColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOutlineVariant, 0)
    private val labelColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant, 0)

    init {
        barPaint.color = barColor
        axisPaint.color = axisColor
        labelPaint.color = labelColor
        minimumHeight = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            72f,
            resources.displayMetrics
        ).toInt()
    }

    fun setBuckets(newBuckets: List<AnalyticsHistogramBucket>, periodMillis: Long) {
        buckets = newBuckets
        periodInMillis = periodMillis
        maxCount = newBuckets.maxOfOrNull { it.count } ?: 0
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (buckets.isEmpty() || maxCount <= 0 || periodInMillis <= 0L) return

        val labelHeight = labelPaint.textSize * 1.4f
        val chartTop = paddingTop.toFloat()
        val chartBottom = height - paddingBottom - labelHeight
        val chartLeft = paddingLeft.toFloat()
        val chartRight = (width - paddingRight).toFloat()
        val chartHeight = max(0f, chartBottom - chartTop)
        val chartWidth = max(0f, chartRight - chartLeft)
        if (chartHeight <= 0f || chartWidth <= 0f) return

        val gap = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            1f,
            resources.displayMetrics
        )
        val barWidth = (chartWidth / buckets.size) - gap
        if (barWidth <= 0f) return

        buckets.forEachIndexed { index, bucket ->
            val left = chartLeft + index * (barWidth + gap)
            val barHeight = if (bucket.count <= 0) {
                0f
            } else {
                chartHeight * (bucket.count.toFloat() / maxCount.toFloat())
            }
            val top = chartBottom - barHeight
            if (barHeight > 0f) {
                canvas.drawRect(left, top, left + barWidth, chartBottom, barPaint)
            }
        }

        canvas.drawLine(chartLeft, chartBottom, chartRight, chartBottom, axisPaint)

        val first = buckets.first().startRatio
        val last = buckets.last().startRatio + buckets.last().widthRatio
        val labelY = height - paddingBottom.toFloat()
        labelPaint.textAlign = Paint.Align.LEFT
        canvas.drawText(durationLabel(first), chartLeft, labelY, labelPaint)
        labelPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText(durationLabel(last), chartRight, labelY, labelPaint)
        if (first < 1.0 && last > 1.0) {
            val oneX = chartLeft + ((1.0 - first) / (last - first)).toFloat() * chartWidth
            axisPaint.alpha = 120
            canvas.drawLine(oneX, chartTop, oneX, chartBottom, axisPaint)
            axisPaint.alpha = 255
            labelPaint.textAlign = Paint.Align.CENTER
            canvas.drawText(durationLabel(1.0), oneX, labelY, labelPaint)
        }
    }

    private fun durationLabel(ratio: Double): String {
        val millis = (ratio * periodInMillis.toDouble()).toLong().coerceAtLeast(0L)
        return AnalyticsChartConfigurator.formatDuration(millis)
    }
}
