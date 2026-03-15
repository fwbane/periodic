package com.enabwf.periodic

import java.util.Date

data class CompletionHistoryItem(
    val completionId: Int,
    val completionTime: Date,
    val taskName: String,
    val tags: String,
    val previousCompletionTime: Date?,
    val taskPeriodInMillis: Long // New field for user-set period
)
