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

    suspend fun getCompletionRecordsForTaskList(taskId: Int): List<CompletionRecord> {
        return taskDao.getCompletionRecordsForTaskList(taskId)
    }

    suspend fun doesTaskNameExist(name: String): Boolean {
        return taskDao.doesTaskNameExist(name)
    }

    suspend fun doesOtherTaskNameExist(name: String, taskIdToExclude: Int): Boolean {
        return taskDao.doesOtherTaskNameExist(name, taskIdToExclude)
    }

    suspend fun getAllTasks(): List<Task> {
        return taskDao.getAllTasks()
    }

    suspend fun getAllCompletionRecords(): List<CompletionRecord> {
        return taskDao.getAllCompletionRecords()
    }

    suspend fun clearDatabase() {
        taskDao.clearAllCompletionRecords()
        taskDao.clearAllTasks()
    }

    suspend fun insertTasks(tasks: List<Task>) {
        taskDao.insertTasks(tasks)
    }

    suspend fun insertCompletionRecords(records: List<CompletionRecord>) {
        taskDao.insertCompletionRecords(records)
    }
}
