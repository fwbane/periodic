package com.enabwf.periodic

import android.content.Context
import com.google.android.material.color.MaterialColors

object AnalyticsChartColors {
    private val materialAttrs = intArrayOf(
        com.google.android.material.R.attr.colorPrimary,
        com.google.android.material.R.attr.colorSecondary,
        com.google.android.material.R.attr.colorTertiary,
        com.google.android.material.R.attr.colorError,
        com.google.android.material.R.attr.colorPrimaryContainer,
        com.google.android.material.R.attr.colorSecondaryContainer,
        com.google.android.material.R.attr.colorTertiaryContainer,
        com.google.android.material.R.attr.colorErrorContainer,
        com.google.android.material.R.attr.colorOutline
    )

    fun seriesColors(context: Context, count: Int): List<Int> {
        if (count <= 0) return emptyList()
        val resolved = materialAttrs.map { attr ->
            MaterialColors.getColor(context, attr, 0)
        }
        return List(count) { index -> resolved[index % resolved.size] }
    }
}
