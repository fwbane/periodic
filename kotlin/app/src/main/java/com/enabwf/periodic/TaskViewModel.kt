package com.enabwf.periodic
import android.app.Application
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

    fun deleteTaskWithHistory(task: Task) = viewModelScope.launch {
        repository.deleteTaskAndHistory(task)
    }

    fun insertCompletionRecord(record: CompletionRecord) = viewModelScope.launch {
        repository.insertCompletionRecord(record)
    }

    fun getCompletionRecordsForTask(taskId: Int): LiveData<List<CompletionRecord>> {
        return repository.getCompletionRecordsForTask(taskId)
    }
}
