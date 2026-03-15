package com.enabwf.periodic

import java.util.Date

object PeriodCalculator {

    /**
     * Calculates the median period (interval) between consecutive completion records.
     * Returns null if there are fewer than 2 records.
     */
    fun calculateMedian(records: List<CompletionRecord>): Long? {
        if (records.size < 2) return null

        val sortedRecords = records.sortedBy { it.completionTime.time }
        val intervals = mutableListOf<Long>()

        for (i in 0 until sortedRecords.size - 1) {
            val diff = sortedRecords[i + 1].completionTime.time - sortedRecords[i].completionTime.time
            intervals.add(diff)
        }

        return getMedian(intervals)
    }

    /**
     * Calculates the median period for the N most recent completion records.
     * e.g. limit = 5 means consider the 5 most recent records (yielding 4 intervals).
     */
    fun calculateRecentMedian(records: List<CompletionRecord>, limit: Int = 5): Long? {
        if (records.size < 2) return null

        // Sort by time descending to easily pick the most recent ones
        val sortedByTimeDesc = records.sortedByDescending { it.completionTime.time }
        
        // Take the 'limit' most recent records. 
        // If limit is 5, we take top 5.
        val recentRecords = sortedByTimeDesc.take(limit)

        // If after taking, we have fewer than 2, we can't calculate an interval
        if (recentRecords.size < 2) return null

        // Now calculate median for these specific records
        return calculateMedian(recentRecords)
    }

    private fun getMedian(values: List<Long>): Long? {
        if (values.isEmpty()) return null

        val sortedValues = values.sorted()
        val size = sortedValues.size

        return if (size % 2 == 0) {
            val mid1 = sortedValues[size / 2 - 1]
            val mid2 = sortedValues[size / 2]
            (mid1 + mid2) / 2
        } else {
            sortedValues[size / 2]
        }
    }
}
