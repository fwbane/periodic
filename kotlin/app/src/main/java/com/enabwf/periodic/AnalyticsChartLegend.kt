package com.enabwf.periodic

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.color.MaterialColors

object AnalyticsChartLegend {
    fun bind(container: ViewGroup, entries: List<LegendEntry>) {
        container.removeAllViews()
        container.visibility = if (entries.isEmpty()) View.GONE else View.VISIBLE
        if (entries.isEmpty()) return

        val density = container.resources.displayMetrics.density
        val swatchSize = (10f * density).toInt().coerceAtLeast(1)
        val swatchMargin = (6f * density).toInt()
        val rowPaddingV = (4f * density).toInt()
        val rowPaddingH = (2f * density).toInt()

        entries.forEach { entry ->
            val row = LinearLayout(container.context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(rowPaddingH, rowPaddingV, rowPaddingH, rowPaddingV)
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }

            val swatch = View(container.context).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(entry.color)
                }
                layoutParams = LinearLayout.LayoutParams(swatchSize, swatchSize).apply {
                    marginEnd = swatchMargin
                }
            }

            val label = TextView(container.context).apply {
                text = entry.label
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
                setTextColor(
                    MaterialColors.getColor(
                        container.context,
                        com.google.android.material.R.attr.colorOnSurfaceVariant,
                        0
                    )
                )
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f
                )
            }

            row.addView(swatch)
            row.addView(label)
            container.addView(row)
        }
    }

    data class LegendEntry(val label: String, val color: Int)
}
