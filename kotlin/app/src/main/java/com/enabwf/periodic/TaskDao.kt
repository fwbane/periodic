package com.enabwf.periodic

import com.enabwf.periodic.Task
import com.enabwf.periodic.CompletionRecord
import androidx.lifecycle.LiveData
import androidx.room.*
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Update
import androidx.room.Delete
@Dao
interface TaskDao {
    @Query("SELECT * FROM task_table ORDER BY dueDate ASC, name ASC")
    fun getAllTasks(): LiveData<List<Task>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(task: Task): Long

    @Update
    suspend fun update(task: Task): Int

    @Delete
    suspend fun delete(task: Task): Int
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCompletionRecord(record: CompletionRecord): Long

    @Query("SELECT * FROM completion_table WHERE taskId = :taskId ORDER BY completionTime DESC")
    fun getCompletionRecordsForTask(taskId: Int): LiveData<List<CompletionRecord>>
}