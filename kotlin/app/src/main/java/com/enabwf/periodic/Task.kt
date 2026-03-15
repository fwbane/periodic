package com.enabwf.periodic
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "task_table")
data class Task(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val periodInMillis: Long, // Recurrence period in milliseconds
    var lastDone: Date? = null, // Last time task was completed
    var dueDate: Date? = null,
    val comments: String? = null, // field for comments
    val tags: List<String> = emptyList(), // field for tags
    val isActive: Boolean = true,
    val medianHistoryPeriod: Long? = null,
    val medianRecentPeriod: Long? = null
)