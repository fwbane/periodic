package com.enabwf.periodic

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import com.google.android.material.color.MaterialColors
import java.time.DayOfWeek

class HeatmapView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    private val cellPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.RIGHT
    }
    private val hourLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }
    private val grid = Array(7) { IntArray(24) }
    private var maxCount = 0

    private val baseColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimary, 0)
    private val emptyColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurfaceVariant, 0)
    private val labelColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant, 0)

    private val dayLabels = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    private val hourTickLabels = listOf(0, 6, 12, 18)

    init {
        val textSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            11f,
            resources.displayMetrics
        )
        labelPaint.textSize = textSize
        hourLabelPaint.textSize = textSize
        labelPaint.color = labelColor
        hourLabelPaint.color = labelColor
    }

    fun setCells(cells: List<AnalyticsHeatmapCell>) {
        for (row in grid) row.fill(0)
        maxCount = 0
        cells.forEach { cell ->
            val dayIndex = cell.dayOfWeek.value - 1
            grid[dayIndex][cell.hour] = cell.completionCount
            maxCount = maxOf(maxCount, cell.completionCount)
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0 || height == 0) return

        val leftPad = 44f
        val bottomPad = 28f
        val gridLeft = leftPad
        val gridTop = 8f
        val gridWidth = width - leftPad - 8f
        val gridHeight = height - gridTop - bottomPad
        if (gridWidth <= 0f || gridHeight <= 0f) return

        val cellWidth = gridWidth / 24f
        val cellHeight = gridHeight / 7f

        DayOfWeek.entries.forEachIndexed { dayIndex, _ ->
            val centerY = gridTop + (dayIndex + 0.5f) * cellHeight
            canvas.drawText(dayLabels[dayIndex], leftPad - 8f, centerY + labelPaint.textSize / 3f, labelPaint)
        }

        hourTickLabels.forEach { hour ->
            val centerX = gridLeft + (hour + 0.5f) * cellWidth
            val label = "%02d".format(hour)
            canvas.drawText(label, centerX, height - 8f, hourLabelPaint)
        }

        for (dayIndex in 0..6) {
            for (hour in 0..23) {
                val count = grid[dayIndex][hour]
                cellPaint.color = colorForCount(count)
                val left = gridLeft + hour * cellWidth
                val top = gridTop + dayIndex * cellHeight
                canvas.drawRect(
                    RectF(left + 1f, top + 1f, left + cellWidth - 1f, top + cellHeight - 1f),
                    cellPaint
                )
            }
        }
    }

    private fun colorForCount(count: Int): Int {
        if (count <= 0 || maxCount == 0) return emptyColor
        val alpha = (40 + (215.0 * count / maxCount)).toInt().coerceIn(40, 255)
        return (alpha shl 24) or (baseColor and 0x00FFFFFF)
    }
}
