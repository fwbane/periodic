package com.enabwf.periodic
import androidx.lifecycle.LiveData

class TaskRepository(private val taskDao: TaskDao) {

    val allTasks: LiveData<List<Task>> = taskDao.getAllActiveTasks()

    suspend fun insert(task: Task) {
        taskDao.insert(task)
    }

    suspend fun update(task: Task) {
        taskDao.update(task)
    }

//    suspend fun deleteTaskAndHistory(task: Task) {
//        taskDao.deleteCompletionRecordsForTask(task.id)
//        taskDao.delete(task) // or taskDao.deleteTaskById(task.id)
//    }

    // Renamed and changed: Marks task as inactive. Completion history is NOT deleted.
    suspend fun markTaskAsInactive(task: Task) {
        taskDao.markAsInactive(task.id)
        // taskDao.deleteCompletionRecordsForTask(task.id)
    }

    // For future "restore task" feature
    suspend fun markTaskAsActive(task: Task) {
        taskDao.markAsActive(task.id)
    }

    suspend fun insertCompletionRecord(record: CompletionRecord) {
        taskDao.insertCompletionRecord(record)
    }

    fun getCompletionRecordsForTask(taskId: Int): LiveData<List<CompletionRecord>> {
        return taskDao.getCompletionRecordsForTask(taskId)
    }
}
