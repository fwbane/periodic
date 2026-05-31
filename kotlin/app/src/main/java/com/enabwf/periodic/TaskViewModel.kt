package com.enabwf.periodic
import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

class TaskViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: TaskRepository
    val allTasks: LiveData<List<Task>>

    init {
        val taskDao = TaskDatabase.getDatabase(application).taskDao()
        repository = TaskRepository(taskDao)
        allTasks = repository.allTasks
    }

    fun insert(task: Task) = viewModelScope.launch {
        repository.insert(task)
    }

    fun update(task: Task) = viewModelScope.launch {
        repository.update(task)
    }

    suspend fun updateTask(task: Task) {
        repository.update(task)
    }

//    fun deleteTaskWithHistory(task: Task) = viewModelScope.launch {
//        repository.deleteTaskAndHistory(task)
//    }

    // Renamed and updated: Calls repository to mark task as inactive
    fun markTaskAsInactive(task: Task) = viewModelScope.launch {
        repository.markTaskAsInactive(task)
    }

    // Optional: For future "restore task" feature
    fun markTaskAsActive(task: Task) = viewModelScope.launch {
        repository.markTaskAsActive(task)
    }

    fun insertCompletionRecord(record: CompletionRecord) = viewModelScope.launch {
        repository.insertCompletionRecord(record)
    }

    suspend fun addCompletionRecord(record: CompletionRecord) {
        repository.insertCompletionRecord(record)
    }

    suspend fun updateCompletionRecord(record: CompletionRecord) {
        repository.updateCompletionRecord(record)
    }

    suspend fun deleteCompletionRecordById(recordId: Int) {
        repository.deleteCompletionRecordById(recordId)
    }

    fun getCompletionRecordsForTask(taskId: Int): LiveData<List<CompletionRecord>> {
        return repository.getCompletionRecordsForTask(taskId)
    }

    suspend fun getCompletionRecordsForTaskList(taskId: Int): List<CompletionRecord> {
        return repository.getCompletionRecordsForTaskList(taskId)
    }

    suspend fun doesTaskNameExist(name: String): Boolean {
        return repository.doesTaskNameExist(name)
    }

    suspend fun doesOtherTaskNameExist(name: String, taskIdToExclude: Int): Boolean {
        return repository.doesOtherTaskNameExist(name, taskIdToExclude)
    }

    suspend fun getAllTasks(): List<Task> {
        return repository.getAllTasks()
    }

    suspend fun getTaskById(taskId: Int): Task? {
        return repository.getTaskById(taskId)
    }

    suspend fun getAllCompletionRecords(): List<CompletionRecord> {
        return repository.getAllCompletionRecords()
    }

    suspend fun replaceDatabase(tasks: List<Task>, records: List<CompletionRecord>) {
        repository.clearDatabase()
        repository.insertTasks(tasks)
        repository.insertCompletionRecords(records)
    }

    fun checkAndBackfillMedians() = viewModelScope.launch {
        val sharedPrefs = getApplication<Application>().getSharedPreferences("periodic_prefs", Context.MODE_PRIVATE)
        val isBackfilled = sharedPrefs.getBoolean("MEDIAN_BACKFILL_COMPLETED", false)

        if (!isBackfilled) {
            val tasks = repository.getAllTasks()
            tasks.forEach { task ->
                val records = repository.getCompletionRecordsForTaskList(task.id)
                val medianHistory = PeriodCalculator.calculateMedian(records)
                val medianRecent = PeriodCalculator.calculateRecentMedian(records)

                // Update if calculated values differ (or if they are null and fields are null, no update needed, but easiest to just check)
                // Since default is null, and we want to populate, we should update.
                val updatedTask = task.copy(
                    medianHistoryPeriod = medianHistory,
                    medianRecentPeriod = medianRecent
                )
                repository.update(updatedTask)
            }
            sharedPrefs.edit().putBoolean("MEDIAN_BACKFILL_COMPLETED", true).apply()
        }
    }
}
