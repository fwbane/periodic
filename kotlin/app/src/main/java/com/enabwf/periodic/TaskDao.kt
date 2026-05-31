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
    @Query("SELECT * FROM task_table WHERE isActive = 1 ORDER BY dueDate ASC, name ASC")
    fun getAllActiveTasks(): LiveData<List<Task>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(task: Task): Long

    @Update
    suspend fun update(task: Task): Int

    // New: Mark a task as inactive (soft delete)
    @Query("UPDATE task_table SET isActive = 0 WHERE id = :taskId")
    suspend fun markAsInactive(taskId: Int): Int

    // Optional: New method to mark a task as active again (for future restore functionality)
    @Query("UPDATE task_table SET isActive = 1 WHERE id = :taskId")
    suspend fun markAsActive(taskId: Int): Int

    @Delete
    suspend fun delete(task: Task): Int

    @Query("DELETE FROM task_table WHERE id = :taskId") // Alternative delete by ID
    suspend fun deleteTaskById(taskId: Int): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCompletionRecord(record: CompletionRecord): Long

    @Update
    suspend fun updateCompletionRecord(record: CompletionRecord): Int

    @Query("DELETE FROM completion_table WHERE id = :recordId")
    suspend fun deleteCompletionRecordById(recordId: Int): Int

    @Query("SELECT * FROM completion_table WHERE taskId = :taskId ORDER BY completionTime DESC")
    fun getCompletionRecordsForTask(taskId: Int): LiveData<List<CompletionRecord>>

    @Query("SELECT * FROM completion_table WHERE taskId = :taskId ORDER BY completionTime DESC")
    suspend fun getCompletionRecordsForTaskList(taskId: Int): List<CompletionRecord>

    @Query("DELETE FROM completion_table WHERE taskId = :taskId") // New: Delete completion records for a task
    suspend fun deleteCompletionRecordsForTask(taskId: Int): Int

    // New: Check if a task name exists (case-insensitive)
    @Query("SELECT EXISTS(SELECT 1 FROM task_table WHERE LOWER(name) = LOWER(:name) LIMIT 1)")
    suspend fun doesTaskNameExist(name: String): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM task_table WHERE LOWER(name) = LOWER(:name) AND id != :taskIdToExclude LIMIT 1)")
    suspend fun doesOtherTaskNameExist(name: String, taskIdToExclude: Int): Boolean

    @Query("SELECT * FROM task_table")
    suspend fun getAllTasks(): List<Task>

    @Query("SELECT * FROM task_table WHERE id = :taskId LIMIT 1")
    suspend fun getTaskById(taskId: Int): Task?

    @Query("SELECT * FROM completion_table")
    suspend fun getAllCompletionRecords(): List<CompletionRecord>

    @Query("DELETE FROM task_table")
    suspend fun clearAllTasks()

    @Query("DELETE FROM completion_table")
    suspend fun clearAllCompletionRecords()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTasks(tasks: List<Task>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCompletionRecords(records: List<CompletionRecord>)

    @Query("""
        SELECT 
            c.id as completionId,
            c.taskId as taskId,
            c.completionTime,
            t.name as taskName,
            t.tags as tags,
            t.periodInMillis as taskPeriodInMillis,
            (
                SELECT c2.completionTime
                FROM completion_table c2
                WHERE c2.taskId = c.taskId AND c2.completionTime < c.completionTime
                ORDER BY c2.completionTime DESC
                LIMIT 1
            ) as previousCompletionTime
        FROM completion_table c
        INNER JOIN task_table t ON c.taskId = t.id
        WHERE (:tagFilter IS NULL OR t.tags LIKE '%' || :tagFilter || '%')
        ORDER BY c.completionTime DESC
        LIMIT :limit OFFSET :offset
    """)
    suspend fun getCompletionHistory(limit: Int, offset: Int, tagFilter: String?): List<CompletionHistoryItem>

    @Query("SELECT DISTINCT tags FROM task_table")
    suspend fun getAllTagsRaw(): List<String>
}