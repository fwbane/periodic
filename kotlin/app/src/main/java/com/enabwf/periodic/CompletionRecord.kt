package com.enabwf.periodic
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Date

@Entity(
    tableName = "completion_table",
    indices = [Index(value = ["taskId", "completionTime"])]
)
data class CompletionRecord(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val taskId: Int,
    val completionTime: Date
)