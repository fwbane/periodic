package com.enabwf.periodic

import android.content.Context

object AnalyticsTagColors {
    const val UNTAGGED = "Untagged"

    fun firstTag(tags: String): String =
        tags.split(",")
            .asSequence()
            .map(String::trim)
            .firstOrNull(String::isNotEmpty)
            ?: UNTAGGED

    fun colorMap(context: Context, tags: Collection<String>): Map<String, Int> {
        val ordered = tags.distinct().sortedWith(
            compareBy<String> { it.equals(UNTAGGED, ignoreCase = true) }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it }
        )
        val colors = AnalyticsChartColors.seriesColors(context, ordered.size.coerceAtLeast(1))
        return ordered.mapIndexed { index, tag -> tag to colors[index % colors.size] }.toMap()
    }

    fun colorFor(context: Context, tag: String, knownTags: Collection<String>): Int {
        val map = colorMap(context, knownTags + tag)
        return map.getValue(tag)
    }
}
