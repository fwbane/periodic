package com.enabwf.periodic
import androidx.lifecycle.LiveData

class TaskRepository(private val taskDao: TaskDao) {

    val allTasks: LiveData<List<Task>> = taskDao.getAllTasks()

    suspend fun insert(task: Task) {
        taskDao.insert(task)
    }

    suspend fun update(task: Task) {
        taskDao.update(task)
    }

    suspend fun deleteTaskAndHistory(task: Task) {
        taskDao.deleteCompletionRecordsForTask(task.id)
        taskDao.delete(task) // or taskDao.deleteTaskById(task.id)
    }
    suspend fun insertCompletionRecord(record: CompletionRecord) {
        taskDao.insertCompletionRecord(record)
    }

    fun getCompletionRecordsForTask(taskId: Int): LiveData<List<CompletionRecord>> {
        return taskDao.getCompletionRecordsForTask(taskId)
    }
}
