package com.enabwf.periodic
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "completion_table")
data class CompletionRecord(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val taskId: Int,
    val completionTime: Date
)