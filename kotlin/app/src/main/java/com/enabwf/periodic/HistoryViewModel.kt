package com.enabwf.periodic

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

class HistoryViewModel(private val repository: TaskRepository) : ViewModel() {

    private val _historyItems = MutableLiveData<List<CompletionHistoryItem>>()
    val historyItems: LiveData<List<CompletionHistoryItem>> = _historyItems

    private val _tags = MutableLiveData<List<String>>()
    val tags: LiveData<List<String>> = _tags

    private var currentPage = 0
    private val pageSize = 20
    private var currentFilter: String? = null
    private var isLastPage = false
    private var isLoading = false

    init {
        loadTags()
        loadHistory(reset = true)
    }

    private fun loadTags() {
        viewModelScope.launch {
            _tags.value = repository.getAllTags()
        }
    }

    fun loadHistory(reset: Boolean = false) {
        if (isLoading && !reset) return // Allow reset even if loading
        if (reset) {
            currentPage = 0
            isLastPage = false
            _historyItems.value = emptyList()
            isLoading = false // Reset loading state
        }
        if (isLastPage) return

        isLoading = true
        viewModelScope.launch {
            val offset = currentPage * pageSize
            val newItems = repository.getCompletionHistory(pageSize, offset, currentFilter)
            
            if (newItems.size < pageSize) {
                isLastPage = true
            }
            
            val currentList = _historyItems.value.orEmpty().toMutableList()
            currentList.addAll(newItems)
            _historyItems.value = currentList
            
            currentPage++
            isLoading = false
        }
    }

    fun setFilter(tag: String?) {
        // Treat "All" or null or empty as no filter
        val filter = if (tag.isNullOrBlank() || tag == "All") null else tag
        if (currentFilter != filter) {
            currentFilter = filter
            loadHistory(reset = true)
        }
    }
}

class HistoryViewModelFactory(private val repository: TaskRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HistoryViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return HistoryViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
